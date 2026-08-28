"""
pdf_generator.py — PDF generation via ReportLab.

Functions:
  - export_absensi_pdf()      : attendance report table
  - generate_name_tag_pdf()   : ID cards (54mm x 86mm) per page
  - generate_rapor_pdf()      : student report card (nilai + absen)
  - generate_store_report_pdf : generic store summary (fallback)
"""
from reportlab.lib.pagesizes import A4, mm
from reportlab.lib.units import mm as mm_unit
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, Image as RLImage, PageBreak
)
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
import os

STYLE = getSampleStyling() if False else None

# ── Helpers ──
def _style_h1():
    s = getSampleStyleSheet()["Heading1"].clone("h1")
    s.fontSize = 16; s.textColor = colors.HexColor("#1A73E8")
    return s
def _style_body():
    return getSampleStyleSheet()["BodyText"]

# Register an Indonesian-friendly font alias (ReportLab ships DejaVuSans which has ID chars)
FONT_DIR = os.path.join(os.getcwd(), "fonts")
try:
    pdfmetrics.registerFont(TTFont("DejaVu", "DejaVuSans.ttf"))
    FONT_FAMILY = "DejaVu"
except Exception:
    FONT_FAMILY = "Helvetica"


# ── Attendance report PDF ──
def export_absensi_pdf(path: str, rows: list[dict], tanggal: str):
    doc = SimpleDocTemplate(path, pagesize=A4, rightMargin=20, leftMargin=20, topMargin=20, bottomMargin=20)
    st = getSampleStyleSheet()
    story = [
        Paragraph("Laporan Absensi Hari " + (tanggal or "-"), _style_h1()),
        Spacer(1, 12),
        Table(
            [["Waktu Masuk", "Waktu Keluar", "Status", "Siswa ID", "Mapel"]] +
            [[r.get("waktu_masuk","-"), r.get("waktu_keluar","-"), r.get("status","-"), r.get("siswa_id","-"), r.get("mapel_id","-")] for r in rows],
            colWidths=[24*mm, 24*mm, 22*mm, 28*mm, 22*mm]
        ).setStyle(TableStyle([
            ("BACKGROUND", (0,0), (-1,0), colors.HexColor("#1A73E8")),
            ("TEXTCOLOR", (0,0), (-1,0), colors.white),
            ("GRID", (0,0), (-1,-1), 0.5, colors.HexColor("#D1D5DB")),
            ("FONTNAME", (0,0), (-1,-1), FONT_FAMILY),
        ]))
    ]
    doc.build(story)
    return path


# ── Name tag (ID card) PDF — 54mm × 86mm per card ──
def generate_name_tag_pdf(path: str, cards: list[dict]):
    W, H = 54*mm, 86*mm
    doc = SimpleDocTemplate(path, pagesize=(W, H), rightMargin=6*mm, leftMargin=6*mm, topMargin=6*mm, bottomMargin=6*mm)
    story = []
    for c in cards:
        # Foto
        flow = []
        if c.get("foto") and os.path.exists(c["foto"]):
            flow.append(RLImage(c["foto"], width=32*mm, height=32*mm, kind="proportional"))
        else:
            flow.append(Paragraph(c["nama"][:1], ParagraphStyle(name="init", fontName=FONT_FAMILY, fontSize=28, textColor=colors.HexColor("#1A73E8"))))
        flow.append(Paragraph(c["nama"], _style_body()))
        flow.append(Paragraph(f"NIS: {c['nis']}", _style_body()))
        flow.append(Paragraph(f"Kelas: {c.get('kelas','')}", _style_body()))
        if c.get("barcode") and os.path.exists(c["barcode"]):
            flow.append(RLImage(c["barcode"], width=40*mm, height=18*mm, kind="proportional"))
        story += flow + [PageBreak()]
    doc.build(story)
    return path


# ── Rapor (student report card) PDF ──
def generate_rapor_pdf(path: str, siswa: dict, nilai_list: list[dict], absensi_today: list[dict]):
    doc = SimpleDocTemplate(path, pagesize=A4, leftMargin=20, rightMargin=20, topMargin=20, bottomMargin=20)
    st = getSampleStyleSheet()
    story = [
        Paragraph("Rapor Siswa", _style_h1()),
        Spacer(1, 8),
        Paragraph(f"Nama      : {siswa.get('nama','-')}", _style_body()),
        Paragraph(f"NIS       : {siswa.get('nis','-')}", _style_body()),
        Paragraph(f"Kelas ID  : {siswa.get('kelas_id','-')}", _style_body()),
        Paragraph(f"Tgl Lahir : {siswa.get('tanggal_lahir','-')}", _style_body()),
        Spacer(1, 12),
        Paragraph("Nilai Per Mapel", ParagraphStyle(name="h2", fontName=FONT_FAMILY, fontSize=13, textColor=colors.HexColor("#1A73E8"))),
        Table(
            [["Mata Pelajaran", "Nilai", "Semester"]] +
            [[n.get("mapel","-"), n.get("nilai","-"), n.get("semester","-")] for n in nilai_list],
            colWidths=[50*mm, 25*mm, 25*mm],
        ).setStyle(TableStyle([("GRID",(0,0),(-1,-1),0.5,colors.HexColor("#D1D5DB")), ("FONTNAME",(0,0),(-1,-1),FONT_FAMILY)])),
        Spacer(1, 12),
        Paragraph("Absensi Hari Ini", ParagraphStyle(name="h2", fontName=FONT_FAMILY, fontSize=13, textColor=colors.HexColor("#1A73E8"))),
    ]
    # attendance counts
    hadir = sum(1 for a in absensi_today if a.get("status")=="Hadir")
    izin = sum(1 for a in absensi_today if a.get("status")=="Izin")
    sakit = sum(1 for a in absensi_today if a.get("status")=="Sakit")
    alfa = sum(1 for a in absensi_today if a.get("status")=="Alfa")
    story += [
        Paragraph(f"Hadir: {hadir} | Izin: {izin} | Sakit: {sakit} | Alfa: {alfa}", _style_body()),
    ]
    doc.build(story)
    return path


# ── Generic store report (fallback for rapor/rekap) ──
def generate_store_report_pdf(path: str, store_name: str, rows: list[dict], headers: list[str], title: str):
    doc = SimpleDocTemplate(path, pagesize=A4, leftMargin=20, rightMargin=20, topMargin=20, bottomMargin=20)
    story = [Paragraph(title, _style_h1()), Spacer(1,12)]
    data = [headers] + [[str(r.get(h.lower().replace(" ", "_"), "-")) for h in headers] for r in rows]
    story.append(Table(data, repeatRows=1).setStyle(TableStyle([
        ("BACKGROUND",(0,0),(-1,0),colors.HexColor("#1A73E8")),("TEXTCOLOR",(0,0),(-1,0),colors.white),
        ("GRID",(0,0),(-1,-1),0.5,colors.HexColor("#D1D5DB")),("FONTNAME",(0,0),(-1,-1),FONT_FAMILY)
    ])))
    doc.build(story)
    return path


# placeholder
def _get_sample_styling():
    return None
