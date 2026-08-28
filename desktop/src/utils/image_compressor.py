"""
image_compressor.py — student photo utilities.

- Crop to 3:4 aspect, resize max 800x1067, JPEG q80.
- Save under data/foto/<tahun>/<kelas_id>/<nis>.jpg.
- Old photo deleted (no orphans on re-capture).
"""
import os
from PIL import Image

MAX_W, MAX_H = 800, 1067
QUALITY = 80

DATA_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "data")


def compress_and_save(src_path: str, tahun_ajaran: str, kelas_id: int, nis: str, ext: str = ".jpg") -> str:
    """Open [src_path], crop to 3:4, resize, save compressed. Returns abs path."""
    dst_dir = os.path.join(DATA_DIR, "foto", tahun_ajaran, str(kelas_id))
    os.makedirs(dst_dir, exist_ok=True)
    dst = os.path.join(dst_dir, f"{nis}{ext}")

    # remove old
    if os.path.exists(dst):
        os.remove(dst)

    with Image.open(src_path) as im:
        cropped = center_crop_34(im)
        scaled = scale_to_fit(cropped)
        scaled.save(dst, "JPEG", quality=QUALITY)
    return dst


def center_crop_34(im: Image.Image) -> Image.Image:
    """Center-crop to a 3:4 portrait aspect ratio."""
    w, h = im.size
    target_ratio = 3 / 4  # width/height — portrait so height is larger
    # We want final W/H = 3/4 (portrait → H > W)
    if w / h > target_ratio:
        # image is wider than 3:4 — crop sides
        new_w = int(h * target_ratio)
        dw = (w - new_w) // 2
        return im.crop((dw, 0, dw + new_w, h))
    else:
        # image is taller than 3:4 — crop top/bottom
        new_h = int(w / target_ratio)
        dh = (h - new_h) // 2
        return im.crop((0, dh, w, dh + new_h))


def scale_to_fit(im: Image.Image) -> Image.Image:
    """Resize keeping aspect; max 800x1067."""
    w, h = im.size
    if w <= MAX_W and h <= MAX_H:
        return im
    ratio = min(MAX_W / w, MAX_H / h, MAX_W / w)  # fit within box
    new_w = int(w * ratio)
    new_h = int(h * ratio)
    return im.resize((new_w, new_h), Image.LANCZOS)


def load_foto(nis: str, tahun_ajaran: str, kelas_id: int) -> Image.Image | None:
    path = os.path.join(DATA_DIR, "foto", tahun_ajaran, str(kelas_id), f"{nis}.jpg")
    return Image.open(path) if os.path.exists(path) else None
