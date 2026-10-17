"""
Minimal, dependency-free NBT reader / writer (Java edition: big-endian, gzip or zlib or raw).

Values are plain Python objects wrapped in small typed classes so that a file read then written again keeps its
tag types:
  Byte, Short, Int, Long (int subclasses), Float, Double (float subclasses), String (str subclass),
  ByteArray, IntArray, LongArray (list subclasses), List (list subclass with an element type), Compound (dict).
When writing, untyped Python values are mapped: bool/int -> Int, float -> Double, str -> String, dict -> Compound,
list -> List (element type taken from the first element).
"""
import gzip
import io
import struct
import zlib

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)


class Byte(int):
	tag = BYTE


class Short(int):
	tag = SHORT


class Int(int):
	tag = INT


class Long(int):
	tag = LONG


class Float(float):
	tag = FLOAT


class Double(float):
	tag = DOUBLE


class String(str):
	tag = STRING


class ByteArray(list):
	tag = BYTE_ARRAY


class IntArray(list):
	tag = INT_ARRAY


class LongArray(list):
	tag = LONG_ARRAY


class List(list):
	tag = LIST

	def __init__(self, items=(), element_tag=None):
		super().__init__(items)
		self.element_tag = element_tag


class Compound(dict):
	tag = COMPOUND


def tag_of(value):
	if hasattr(value, "tag"):
		return value.tag
	if isinstance(value, bool) or isinstance(value, int):
		return INT
	if isinstance(value, float):
		return DOUBLE
	if isinstance(value, str):
		return STRING
	if isinstance(value, dict):
		return COMPOUND
	if isinstance(value, (list, tuple)):
		return LIST
	raise TypeError(f"cannot store {type(value).__name__} in NBT")


# ------------------------------------------------------------------------------------------------------- reading

class _Reader:
	def __init__(self, data):
		self.data = data
		self.pos = 0

	def take(self, n):
		chunk = self.data[self.pos:self.pos + n]
		if len(chunk) != n:
			raise ValueError("truncated NBT data")
		self.pos += n
		return chunk

	def unpack(self, fmt):
		size = struct.calcsize(fmt)
		return struct.unpack(fmt, self.take(size))[0]

	def string(self):
		length = self.unpack(">H")
		return self.take(length).decode("utf-8", errors="surrogateescape")

	def payload(self, tag):
		if tag == BYTE:
			return Byte(self.unpack(">b"))
		if tag == SHORT:
			return Short(self.unpack(">h"))
		if tag == INT:
			return Int(self.unpack(">i"))
		if tag == LONG:
			return Long(self.unpack(">q"))
		if tag == FLOAT:
			return Float(self.unpack(">f"))
		if tag == DOUBLE:
			return Double(self.unpack(">d"))
		if tag == BYTE_ARRAY:
			n = self.unpack(">i")
			return ByteArray(struct.unpack(f">{n}b", self.take(n)))
		if tag == STRING:
			return String(self.string())
		if tag == LIST:
			element = self.unpack(">b")
			n = self.unpack(">i")
			return List([self.payload(element) for _ in range(n)], element)
		if tag == COMPOUND:
			result = Compound()
			while True:
				child = self.unpack(">b")
				if child == END:
					return result
				name = self.string()
				result[name] = self.payload(child)
		if tag == INT_ARRAY:
			n = self.unpack(">i")
			return IntArray(struct.unpack(f">{n}i", self.take(4 * n)))
		if tag == LONG_ARRAY:
			n = self.unpack(">i")
			return LongArray(struct.unpack(f">{n}q", self.take(8 * n)))
		raise ValueError(f"unknown NBT tag {tag}")


def decompress(data):
	if data[:2] == b"\x1f\x8b":
		return gzip.decompress(data)
	if data[:1] == b"\x78":
		try:
			return zlib.decompress(data)
		except zlib.error:
			pass
	return data


def loads(data):
	"""Parses an (optionally compressed) NBT file; returns (root name, Compound)."""
	reader = _Reader(decompress(data))
	tag = reader.unpack(">b")
	if tag != COMPOUND:
		raise ValueError("NBT root is not a compound")
	name = reader.string()
	return name, reader.payload(COMPOUND)


def load(path):
	with open(path, "rb") as f:
		return loads(f.read())[1]


# ------------------------------------------------------------------------------------------------------- writing

def _write_string(out, text):
	raw = str(text).encode("utf-8", errors="surrogateescape")
	out.write(struct.pack(">H", len(raw)))
	out.write(raw)


def _write_payload(out, tag, value):
	if tag == BYTE:
		out.write(struct.pack(">b", int(value)))
	elif tag == SHORT:
		out.write(struct.pack(">h", int(value)))
	elif tag == INT:
		out.write(struct.pack(">i", int(value)))
	elif tag == LONG:
		out.write(struct.pack(">q", int(value)))
	elif tag == FLOAT:
		out.write(struct.pack(">f", float(value)))
	elif tag == DOUBLE:
		out.write(struct.pack(">d", float(value)))
	elif tag == BYTE_ARRAY:
		out.write(struct.pack(">i", len(value)))
		out.write(struct.pack(f">{len(value)}b", *value))
	elif tag == STRING:
		_write_string(out, value)
	elif tag == LIST:
		element = getattr(value, "element_tag", None)
		if value:
			first = tag_of(value[0])
			element = first if element in (None, END) else element
		elif element is None:
			element = END
		out.write(struct.pack(">b", element))
		out.write(struct.pack(">i", len(value)))
		for item in value:
			if tag_of(item) != element and not (element in (INT, LONG, SHORT, BYTE) and isinstance(item, int)):
				raise TypeError("NBT lists must be homogeneous")
			_write_payload(out, element, item)
	elif tag == COMPOUND:
		for key, child in value.items():
			child_tag = tag_of(child)
			out.write(struct.pack(">b", child_tag))
			_write_string(out, key)
			_write_payload(out, child_tag, child)
		out.write(b"\x00")
	elif tag == INT_ARRAY:
		out.write(struct.pack(">i", len(value)))
		out.write(struct.pack(f">{len(value)}i", *value))
	elif tag == LONG_ARRAY:
		out.write(struct.pack(">i", len(value)))
		out.write(struct.pack(f">{len(value)}q", *value))
	else:
		raise ValueError(f"unknown NBT tag {tag}")


def dumps(root, name="", compress=True):
	out = io.BytesIO()
	out.write(struct.pack(">b", COMPOUND))
	_write_string(out, name)
	_write_payload(out, COMPOUND, root)
	data = out.getvalue()
	# mtime=0: identical inputs give identical files (clean diffs)
	return gzip.compress(data, mtime=0) if compress else data


def save(path, root, name=""):
	with open(path, "wb") as f:
		f.write(dumps(root, name))


# ----------------------------------------------------------------------------------------------- SNBT (output)

def to_snbt(value):
	"""Compact SNBT text, for reports and debugging."""
	tag = tag_of(value)
	if tag == COMPOUND:
		return "{" + ",".join(f"{_snbt_key(k)}:{to_snbt(v)}" for k, v in value.items()) + "}"
	if tag == LIST:
		return "[" + ",".join(to_snbt(v) for v in value) + "]"
	if tag == STRING:
		return '"' + str(value).replace("\\", "\\\\").replace('"', '\\"') + '"'
	suffix = {BYTE: "b", SHORT: "s", LONG: "L", FLOAT: "f", DOUBLE: "d"}.get(tag, "")
	if tag in (BYTE_ARRAY, INT_ARRAY, LONG_ARRAY):
		prefix = {BYTE_ARRAY: "B;", INT_ARRAY: "I;", LONG_ARRAY: "L;"}[tag]
		return "[" + prefix + ",".join(str(v) for v in value) + "]"
	return f"{value}{suffix}"


def _snbt_key(key):
	return key if key.replace("_", "").replace("-", "").replace(".", "").isalnum() else to_snbt(String(key))
