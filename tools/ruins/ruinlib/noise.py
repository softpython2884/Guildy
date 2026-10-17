"""Deterministic gradient noise (Perlin style) in 2D / 3D + fractal sums, pure Python."""
import math
import random


class Noise:
	def __init__(self, seed):
		rnd = random.Random(seed)
		perm = list(range(256))
		rnd.shuffle(perm)
		self.p = perm + perm
		self.g3 = [(1, 1, 0), (-1, 1, 0), (1, -1, 0), (-1, -1, 0), (1, 0, 1), (-1, 0, 1), (1, 0, -1), (-1, 0, -1),
			(0, 1, 1), (0, -1, 1), (0, 1, -1), (0, -1, -1)]

	@staticmethod
	def _fade(t):
		return t * t * t * (t * (t * 6 - 15) + 10)

	def n3(self, x, y, z):
		"""Noise in about [-1, 1]."""
		p = self.p
		xi, yi, zi = math.floor(x), math.floor(y), math.floor(z)
		xf, yf, zf = x - xi, y - yi, z - zi
		xi &= 255
		yi &= 255
		zi &= 255
		u, v, w = self._fade(xf), self._fade(yf), self._fade(zf)
		g = self.g3

		def grad(h, dx, dy, dz):
			gx, gy, gz = g[h % 12]
			return gx * dx + gy * dy + gz * dz

		aaa = p[p[p[xi] + yi] + zi]
		aba = p[p[p[xi] + yi + 1] + zi]
		aab = p[p[p[xi] + yi] + zi + 1]
		abb = p[p[p[xi] + yi + 1] + zi + 1]
		baa = p[p[p[xi + 1] + yi] + zi]
		bba = p[p[p[xi + 1] + yi + 1] + zi]
		bab = p[p[p[xi + 1] + yi] + zi + 1]
		bbb = p[p[p[xi + 1] + yi + 1] + zi + 1]
		x1 = _lerp(grad(aaa, xf, yf, zf), grad(baa, xf - 1, yf, zf), u)
		x2 = _lerp(grad(aba, xf, yf - 1, zf), grad(bba, xf - 1, yf - 1, zf), u)
		y1 = _lerp(x1, x2, v)
		x1 = _lerp(grad(aab, xf, yf, zf - 1), grad(bab, xf - 1, yf, zf - 1), u)
		x2 = _lerp(grad(abb, xf, yf - 1, zf - 1), grad(bbb, xf - 1, yf - 1, zf - 1), u)
		y2 = _lerp(x1, x2, v)
		return _lerp(y1, y2, w)

	def n2(self, x, z):
		return self.n3(x, 0.5, z)

	def fbm3(self, x, y, z, octaves=3, lacunarity=2.0, gain=0.5):
		total, amp, freq, norm = 0.0, 1.0, 1.0, 0.0
		for _ in range(octaves):
			total += amp * self.n3(x * freq, y * freq, z * freq)
			norm += amp
			amp *= gain
			freq *= lacunarity
		return total / norm

	def fbm2(self, x, z, octaves=3):
		return self.fbm3(x, 0.37, z, octaves)


def _lerp(a, b, t):
	return a + t * (b - a)
