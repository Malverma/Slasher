"""Generates the temporary sounds in assets/sfx/ (mono 16-bit 22.05 kHz WAV). Standard library only."""
import math, random, struct, wave, os

RATE = 22050
OUT = os.path.join(os.path.dirname(__file__), "..", "assets", "sfx")
random.seed(7)
TAU = 2 * math.pi


def write(name, samples, gain=0.9):
    peak = max(1e-6, max(abs(s) for s in samples))
    with wave.open(os.path.join(OUT, name), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(s / peak * gain * 32767)) for s in samples))


def env(i, n, attack=0.005, power=2.0):
    t = i / RATE
    a = min(1.0, t / attack) if attack > 0 else 1.0
    return a * (1 - i / n) ** power


def noise():
    return random.random() * 2 - 1


class LowPass:
    def __init__(self): self.y = 0.0
    def __call__(self, x, k):
        self.y += k * (x - self.y)
        return self.y


def click():
    n = int(RATE * 0.05)
    return [math.sin(TAU * 900 * i / RATE) * env(i, n, 0.001, 3) for i in range(n)]


def whoosh(dur=0.35):
    n = int(RATE * dur); lp = LowPass(); lp2 = LowPass(); out = []
    for i in range(n):
        p = i / n
        k = 0.04 + 0.25 * math.sin(math.pi * p)
        x = lp(noise(), k)
        out.append((x - lp2(x, 0.02)) * math.sin(math.pi * p) ** 1.5)
    return out


def whirr():
    n = int(RATE * 0.5); lp = LowPass(); out = []
    for i in range(n):
        t = i / RATE; p = i / n
        mod = 0.5 + 0.5 * math.sin(TAU * 22 * t)
        out.append(lp(noise(), 0.18) * mod * math.sin(math.pi * p))
    return out


def cut_soft():
    n = int(RATE * 0.2); lp = LowPass(); out = []
    for i in range(n):
        t = i / RATE; p = i / n
        squish = lp(noise(), 0.12) * (1 - p) ** 2
        tone = math.sin(TAU * (260 - 120 * p) * t) * 0.35 * (1 - p) ** 3
        out.append((squish + tone) * min(1, t / 0.004))
    return out


def cut_wood():
    n = int(RATE * 0.22); lp = LowPass(); out = []
    for i in range(n):
        t = i / RATE; p = i / n
        crackle = (noise() if random.random() < 0.08 else 0) * (1 - p) ** 1.5
        body = lp(noise(), 0.3) * 0.5 * (1 - p) ** 3
        tone = math.sin(TAU * 420 * t) * 0.25 * (1 - p) ** 6
        out.append(crackle + body + tone)
    return out


def cut_hard():
    n = int(RATE * 0.28); lp = LowPass(); out = []
    for i in range(n):
        t = i / RATE; p = i / n
        x = noise()
        crack = (x - lp(x, 0.15)) * (1 - p) ** 6
        ring = (math.sin(TAU * 2300 * t) + 0.5 * math.sin(TAU * 3700 * t)) * 0.22 * (1 - p) ** 3
        out.append(crack + ring)
    return out


def cut_metal():
    n = int(RATE * 0.65); lp = LowPass(); out = []
    for i in range(n):
        t = i / RATE; p = i / n
        ring = (math.sin(TAU * 1250 * t) + 0.6 * math.sin(TAU * 2930 * t) + 0.35 * math.sin(TAU * 4410 * t)) * (1 - p) ** 2.5
        x = noise()
        scrape = (x - lp(x, 0.2)) * 0.8 * max(0, 1 - p * 5)
        out.append(0.4 * ring + scrape)
    return out


def slide():
    n = int(RATE * 1.6); lp = LowPass(); out = []
    for i in range(n):
        p = i / n
        out.append(lp(noise(), 0.09) * min(1, p * 6) * (1 - p) ** 0.6)
    return out


def thud(f0, f1, dur, noise_amt):
    n = int(RATE * dur); lp = LowPass(); out = []; ph = 0
    for i in range(n):
        p = i / n
        ph += TAU * (f0 + (f1 - f0) * p) / RATE
        out.append((math.sin(ph) + noise_amt * lp(noise(), 0.1)) * env(i, n, 0.003, 3))
    return out


def clank():
    n = int(RATE * 0.4); out = []
    for i in range(n):
        t = i / RATE; p = i / n
        ring = sum(a * math.sin(TAU * f * t) for f, a in ((870, 1), (1730, 0.7), (2610, 0.5), (3980, 0.3)))
        hit = noise() * max(0, 1 - p * 12)
        out.append(0.45 * ring * (1 - p) ** 3 + hit)
    return out


def bell(freqs, dur):
    n = int(RATE * dur); out = []
    for i in range(n):
        t = i / RATE
        s = 0
        for k, f in enumerate(freqs):
            start = k * 0.07
            if t >= start:
                tt = t - start
                s += math.sin(TAU * f * tt) * math.exp(-tt * 6) * min(1, tt / 0.003)
                s += 0.3 * math.sin(TAU * f * 2.01 * tt) * math.exp(-tt * 9)
        out.append(s)
    return out


def snap():
    n = int(RATE * 0.7); lp = LowPass(); out = []
    for i in range(n):
        t = i / RATE; p = i / n
        x = noise()
        crack = (x - lp(x, 0.1)) * max(0, 1 - p * 8)
        ring = math.sin(TAU * (1800 - 900 * p) * t) * 0.35 * (1 - p) ** 2
        out.append(crack + ring)
    return out


def arpeggio(freqs, step):
    out = []
    for f in freqs:
        n = int(RATE * step * 2)
        for i in range(n):
            t = i / RATE
            out.append(math.sin(TAU * f * t) * env(i, n, 0.004, 2))
    # overlap-add would be nicer; a plain sequence is fine for a temporary sound
    return out


def music():
    """A slow, seamless 16 s ambient pad loop: Cmaj7 – Am7 – Fmaj7 – G6."""
    length = 16.0
    chords = [
        [130.81, 196.00, 246.94, 329.63],
        [110.00, 164.81, 196.00, 261.63],
        [87.31, 174.61, 220.00, 329.63],
        [98.00, 146.83, 196.00, 246.94],
    ]
    n = int(RATE * length); out = []
    for i in range(n):
        t = i / RATE
        s = 0
        for k, chord in enumerate(chords):
            centre = 4 * k + 2
            dt = (t - centre + length / 2) % length - length / 2
            if abs(dt) >= 3: continue
            w = math.cos(math.pi * dt / 6) ** 2
            for f in chord:
                s += w * (math.sin(TAU * f * t) + 0.35 * math.sin(TAU * f * 2.003 * t) + 0.2 * math.sin(TAU * f * 0.5 * t))
        out.append(s * (1 + 0.08 * math.sin(TAU * 0.25 * t)))
    return out


write("click.wav", click())
write("whoosh.wav", whoosh())
write("whirr.wav", whirr())
write("cut_soft.wav", cut_soft())
write("cut_wood.wav", cut_wood())
write("cut_hard.wav", cut_hard())
write("cut_metal.wav", cut_metal())
write("slide.wav", slide())
write("whump.wav", thud(140, 55, 0.35, 0.6))
write("thunk.wav", thud(95, 60, 0.28, 0.25))
write("thock.wav", thud(230, 160, 0.16, 0.8))
write("clank.wav", clank())
write("chime.wav", bell([1318.5, 1760.0], 0.7))
write("snap.wav", snap())
write("zone.wav", arpeggio([523.25, 659.25, 783.99, 1046.5], 0.09))
write("coins.wav", arpeggio([1568, 2093], 0.05))
write("music.wav", music(), gain=0.6)
