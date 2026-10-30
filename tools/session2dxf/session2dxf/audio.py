"""Audio helpers for the dictaphone (lot 12): reading, LumiSite chirp (2–6 kHz, 60 ms, Hann, 48 kHz) detection."""
from __future__ import annotations

import subprocess
import wave
from pathlib import Path

import numpy as np

SR = 48_000


def chirp(sr: int = SR, f0: float = 2000.0, f1: float = 6000.0, ms: float = 60.0) -> np.ndarray:
    """Same signal as core Chirp.generate."""
    n = int(sr * ms / 1000.0)
    t = np.arange(n) / sr
    k = (f1 - f0) / (n / sr)
    win = 0.5 - 0.5 * np.cos(2 * np.pi * np.arange(n) / (n - 1))
    return (win * np.sin(2 * np.pi * (f0 * t + 0.5 * k * t * t))).astype(np.float32)


def read_audio(path: str | Path, sr: int = SR) -> np.ndarray:
    """Mono float32 in [-1, 1]. WAV 16 bits natively, anything else (FLAC…) through ffmpeg."""
    path = Path(path)
    if path.suffix.lower() == ".wav":
        with wave.open(str(path)) as w:
            if w.getsampwidth() == 2 and w.getframerate() == sr:
                x = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768
                return x.reshape(-1, w.getnchannels()).mean(axis=1)
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-f", "s16le", "-ac", "1", "-ar", str(sr), "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768


def detect_chirps(x: np.ndarray, sr: int = SR, min_score: float = 0.3, min_snr: float = 8.0, min_gap_s: float = 0.4) -> list[dict]:
    """Every chirp of a long recording: normalised cross-correlation (FFT), peaks above thresholds, ≥ min_gap apart."""
    tpl = chirp(sr)
    m = len(tpl)
    if len(x) < m:
        return []
    nfft = 1 << int(np.ceil(np.log2(len(x) + m)))
    corr = np.fft.irfft(np.fft.rfft(x, nfft) * np.conj(np.fft.rfft(tpl, nfft)), nfft)[: len(x) - m + 1]
    e = np.concatenate([[0.0], np.cumsum(x.astype(np.float64) ** 2)])
    win = np.sqrt(np.maximum(e[m:] - e[:-m], 1e-12))
    score = np.abs(corr) / (win * np.linalg.norm(tpl))
    floor = float(np.median(score)) or 1e-12
    out: list[dict] = []
    gap = int(min_gap_s * sr)
    for i in np.argsort(score)[::-1]:
        s = float(score[i])
        if s < min_score or s / floor < min_snr:
            break
        if all(abs(int(i) - c["sample_index"]) >= gap for c in out):
            out.append({"sample_index": int(i), "score": round(s, 4), "snr": round(s / floor, 1)})
    return sorted(out, key=lambda c: c["sample_index"])
