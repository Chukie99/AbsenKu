"""
barcode_generator.py — Code128 & QR generation for name-tags & student IDs.

Uses python-barcode + Pillow. Saved as PNG at 300 DPI for crisp print.
"""
import barcode
from barcode.writer import ImageWriter
import qrcode
from PIL import Image
import io


def generate_code128(value: str, output_path: str, scale: int = 4) -> str:
    """Generate a Code128 barcode PNG. Returns output path."""
    writer = ImageWriter()
    writer.dpi = (300, 300)
    barcode_svg = barcode.get('code128', value, writer=writer)
    barcode_img = barcode_svg.render({
        "module_size": scale,
        "font_size": 10,
        "text_distance": 2.0,
        "quiet_section": 2,
    })  # returns BytesIO
    barcode_img.save(output_path)
    return output_path


def generate_qr(value: str, output_path: str, size: int = 300) -> str:
    """Generate a QR code PNG."""
    qr = qrcode.QRCode(
        version=2,
        error_correction=qrcode.constants.ERROR_CORRECT_M,
        box_size=10,
        border=2,
    )
    qr.add_data(value)
    qr.make(fit=True)
    img = qr.make_image(fill_color="black", back_color="white").convert("RGB")
    img = img.resize((size, size), Image.LANCZOS)
    img.save(output_path)
    return output_path


def generate_combined_id_card(barcode_value: str, output_path: str) -> str:
    """Full-width barcode with quiet zone, saved at 300dpi."""
    return generate_code128(barcode_value, output_path, scale=6)


if __name__ == "__main__":
    # smoke test
    import os
    os.makedirs("/tmp/absenku", exist_ok=True) if os.name != "nt" else None
    p = generate_code128("1234567890", r"C:\file ku\AbsenKu\desktop\data\test_barcode.png")
    print("barcode:", p)
