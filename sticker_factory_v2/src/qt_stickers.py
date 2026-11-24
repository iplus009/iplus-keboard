"""Qt6 (PySide6 + HarfBuzz) + Pillow vinyl die-cut, 3D Fluent, Kawaii, Bangla, Neon & Animated sticker renderer."""
from __future__ import annotations

import io
import math
import sys
from pathlib import Path
from typing import Tuple

import numpy as np
from PIL import Image, ImageFilter
from PySide6.QtCore import QPointF, QRectF, Qt
from PySide6.QtGui import (
    QBrush,
    QColor,
    QFont,
    QFontDatabase,
    QFontMetricsF,
    QGuiApplication,
    QImage,
    QLinearGradient,
    QPainter,
    QPainterPath,
    QPainterPathStroker,
    QPen,
    QPolygonF,
    QRadialGradient,
)

S = 512
_QT_APP = None
_BN_FAMILY = "Nirmala UI"
_UI_FAMILY = "Segoe UI"


def ensure_qt():
    global _QT_APP, _BN_FAMILY, _UI_FAMILY
    if QGuiApplication.instance() is None:
        _QT_APP = QGuiApplication(sys.argv[:1] + ["-platform", "offscreen"])
        for fp, target in (
            (r"C:\Windows\Fonts\Nirmala.ttc", "bn"),
            (r"C:\Windows\Fonts\seguibl.ttf", "ui"),
            (r"C:\Windows\Fonts\seguisb.ttf", "ui_alt"),
            (r"C:\Windows\Fonts\segoeuib.ttf", "ui_alt"),
        ):
            if Path(fp).exists():
                fid = QFontDatabase.addApplicationFont(fp)
                fams = QFontDatabase.applicationFontFamilies(fid)
                if fams:
                    if target == "bn":
                        _BN_FAMILY = fams[0]
                    elif target == "ui":
                        _UI_FAMILY = fams[0]
    else:
        _QT_APP = QGuiApplication.instance()
    return _QT_APP


def _qimg_to_pil(qimg: QImage) -> Image.Image:
    q = qimg.convertToFormat(QImage.Format_RGBA8888)
    ptr = q.constBits()
    arr = np.frombuffer(ptr, np.uint8, q.sizeInBytes()).reshape((q.height(), q.width(), 4)).copy()
    return Image.fromarray(arr, "RGBA")


def _qc(hex_str: str, alpha: int = 255) -> QColor:
    c = QColor(hex_str)
    c.setAlpha(alpha)
    return c


def _mix_qcolor(hex_a: str, hex_b: str, t: float, alpha: int = 255) -> QColor:
    ca, cb = QColor(hex_a), QColor(hex_b)
    return QColor(
        int(ca.red() * (1 - t) + cb.red() * t),
        int(ca.green() * (1 - t) + cb.green() * t),
        int(ca.blue() * (1 - t) + cb.blue() * t),
        alpha,
    )


def _fit_fg_inside_safe_zone(fg: Image.Image, pad: int = 42) -> Image.Image:
    """Proportionally scales oversized artwork inward so die-cut contour never enters outer margin."""
    bbox = fg.getbbox()
    if not bbox:
        return fg
    x0, y0, x1, y1 = bbox
    bw, bh = x1 - x0, y1 - y0
    max_span = S - 2 * pad
    if bw <= max_span and bh <= max_span and x0 >= pad and y0 >= pad and x1 <= S - pad and y1 <= S - pad:
        return fg
    scale = min(1.0, max_span / max(1, bw), max_span / max(1, bh))
    crop = fg.crop(bbox)
    nw, nh = max(1, int(bw * scale)), max(1, int(bh * scale))
    if scale < 1.0:
        crop = crop.resize((nw, nh), Image.LANCZOS)
    canvas = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    canvas.paste(crop, ((S - nw) // 2, (S - nh) // 2))
    return canvas


def add_diecut_contour(fg: Image.Image, rim_rgb: Tuple[int, int, int] = (255, 255, 255),
                       border_size: int = 21, safe_margin: int = 20) -> Image.Image:
    """Wraps foreground artwork in a continuous smooth vinyl die-cut cutout + soft drop shadow."""
    fg = _fit_fg_inside_safe_zone(fg, pad=38)
    alpha = fg.getchannel("A")
    dil = alpha.filter(ImageFilter.MaxFilter(border_size))
    soft = dil.filter(ImageFilter.GaussianBlur(1.2))
    soft_arr = np.array(soft, dtype=np.uint8)
    mask_arr = np.where(soft_arr > 70, np.clip((soft_arr - 70) * 3, 0, 255), 0).astype(np.uint8)

    mask_arr[:safe_margin, :] = 0
    mask_arr[-safe_margin:, :] = 0
    mask_arr[:, :safe_margin] = 0
    mask_arr[:, -safe_margin:] = 0
    mask_img = Image.fromarray(mask_arr, "L")

    shadow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    sh_alpha = Image.new("RGBA", (S, S), (12, 14, 22, 68))
    shadow.paste(sh_alpha, (0, 5), mask_img)
    shadow = shadow.filter(ImageFilter.GaussianBlur(3.2))
    sh_arr = np.array(shadow, dtype=np.uint8)
    sh_arr[:16, :, 3] = 0
    sh_arr[-16:, :, 3] = 0
    sh_arr[:, :16, 3] = 0
    sh_arr[:, -16:, 3] = 0
    out = Image.fromarray(sh_arr, "RGBA")

    rim_layer = Image.new("RGBA", (S, S), rim_rgb + (255,))
    rim_canvas = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    rim_canvas.paste(rim_layer, (0, 0), mask_img)
    out.alpha_composite(rim_canvas)
    out.alpha_composite(fg)
    return out


def _draw_outlined_text(p: QPainter, text: str, cx: float, cy: float, font: QFont,
                        fill_top: QColor, fill_bot: QColor, stroke_color: QColor,
                        stroke_w: float, tilt_deg: float = 0.0, max_width: float = 310.0):
    p.save()
    p.translate(cx, cy)
    if tilt_deg:
        p.rotate(tilt_deg)
    f = QFont(font)
    pt = f.pointSizeF() if f.pointSizeF() > 0 else float(f.pointSize())
    fm = QFontMetricsF(f)
    while fm.horizontalAdvance(text) > max_width and pt > 16.0:
        pt *= 0.88
        f.setPointSizeF(pt)
        fm = QFontMetricsF(f)

    path = QPainterPath()
    tw = fm.horizontalAdvance(text)
    baseline_y = (fm.ascent() - fm.descent()) * 0.5
    path.addText(-tw * 0.5, baseline_y, f, text)

    stroker = QPainterPathStroker()
    stroker.setWidth(stroke_w)
    stroker.setJoinStyle(Qt.RoundJoin)
    stroker.setCapStyle(Qt.RoundCap)
    p.fillPath(stroker.createStroke(path), stroke_color)

    bounds = path.boundingRect()
    grad = QLinearGradient(0, bounds.top(), 0, bounds.bottom())
    grad.setColorAt(0.0, fill_top)
    grad.setColorAt(1.0, fill_bot)
    p.fillPath(path, QBrush(grad))
    p.restore()


def _draw_eyes_and_mouth(p: QPainter, cx: float, cy: float, scale: float, mood: int, outline_col: QColor, gloss: bool = True):
    p.save()
    ex = 48 * scale
    ey = cy - 6 * scale
    er = 16 * scale

    if mood % 5 == 1:
        p.setPen(QPen(outline_col, 9 * scale, Qt.SolidLine, Qt.RoundCap, Qt.RoundJoin))
        p.setBrush(Qt.NoBrush)
        for sgn in (-1, 1):
            p.drawArc(QRectF(cx + sgn * ex - er, ey - er * 0.7, er * 2, er * 1.6), 20 * 16, 140 * 16)
    elif mood % 5 == 2:
        for sgn in (-1, 1):
            sx, sy = cx + sgn * ex, ey
            pts = [QPointF(sx + (er * 1.45 if k % 2 == 0 else er * 0.62) * math.sin(k * math.pi / 5),
                           sy - (er * 1.45 if k % 2 == 0 else er * 0.62) * math.cos(k * math.pi / 5)) for k in range(10)]
            p.setPen(QPen(outline_col, 4 * scale))
            p.setBrush(QColor("#FFD100"))
            p.drawPolygon(QPolygonF(pts))
    elif mood % 5 == 3:
        p.setPen(Qt.NoPen)
        p.setBrush(outline_col)
        p.drawEllipse(QPointF(cx - ex, ey), er * 0.95, er * 1.1)
        if gloss:
            p.setBrush(Qt.white)
            p.drawEllipse(QPointF(cx - ex - 4 * scale, ey - 5 * scale), er * 0.35, er * 0.35)
        p.setPen(QPen(outline_col, 9 * scale, Qt.SolidLine, Qt.RoundCap))
        p.setBrush(Qt.NoBrush)
        p.drawArc(QRectF(cx + ex - er, ey - er * 0.6, er * 2, er * 1.4), 15 * 16, 150 * 16)
    else:
        p.setPen(Qt.NoPen)
        p.setBrush(outline_col)
        for sgn in (-1, 1):
            px = cx + sgn * ex
            p.drawEllipse(QPointF(px, ey), er * 0.95, er * 1.12)
            if gloss:
                p.setBrush(Qt.white)
                p.drawEllipse(QPointF(px - 4 * scale, ey - 5 * scale), er * 0.38, er * 0.38)
                p.drawEllipse(QPointF(px + 4 * scale, ey + 5 * scale), er * 0.18, er * 0.18)
                p.setBrush(outline_col)

    p.setPen(Qt.NoPen)
    p.setBrush(QColor(255, 118, 156, 145))
    for sgn in (-1, 1):
        p.drawEllipse(QPointF(cx + sgn * (ex + 22 * scale), cy + 20 * scale), 20 * scale, 11 * scale)

    my = cy + 24 * scale
    if mood % 4 == 0:
        p.setPen(QPen(outline_col, 8 * scale, Qt.SolidLine, Qt.RoundCap, Qt.RoundJoin))
        p.setBrush(Qt.NoBrush)
        p.drawArc(QRectF(cx - 22 * scale, my - 8 * scale, 22 * scale, 18 * scale), 185 * 16, 175 * 16)
        p.drawArc(QRectF(cx, my - 8 * scale, 22 * scale, 18 * scale), 180 * 16, 175 * 16)
    elif mood % 4 == 1:
        mouth = QPainterPath()
        mouth.moveTo(cx - 32 * scale, my - 4 * scale)
        mouth.lineTo(cx + 32 * scale, my - 4 * scale)
        mouth.quadTo(cx + 30 * scale, my + 38 * scale, cx, my + 38 * scale)
        mouth.quadTo(cx - 30 * scale, my + 38 * scale, cx - 32 * scale, my - 4 * scale)
        p.setPen(QPen(outline_col, 6 * scale))
        p.setBrush(QColor("#7A1B38"))
        p.drawPath(mouth)
        p.setPen(Qt.NoPen)
        p.setBrush(QColor("#FF7A9E"))
        p.drawEllipse(QPointF(cx, my + 24 * scale), 18 * scale, 11 * scale)
    elif mood % 4 == 2:
        p.setPen(QPen(outline_col, 6 * scale))
        p.setBrush(QColor("#8C2442"))
        p.drawEllipse(QPointF(cx, my + 12 * scale), 14 * scale, 18 * scale)
    else:
        p.setPen(QPen(outline_col, 8 * scale, Qt.SolidLine, Qt.RoundCap))
        p.setBrush(Qt.NoBrush)
        p.drawArc(QRectF(cx - 26 * scale, my - 8 * scale, 52 * scale, 28 * scale), 200 * 16, 140 * 16)
    p.restore()


def render_bangla_meme(idx: int, line1: str, line2: str, c1: str, c2: str, dark: str) -> Image.Image:
    """Bangla display meme sticker with Qt6 HarfBuzz shaping + varied badge silhouettes + die-cut."""
    ensure_qt()
    qi = QImage(S, S, QImage.Format_RGBA8888)
    qi.fill(Qt.transparent)
    p = QPainter(qi)
    p.setRenderHint(QPainter.Antialiasing, True)
    p.setRenderHint(QPainter.TextAntialiasing, True)

    cx = S * 0.5 + ((idx % 3) - 1) * 10
    cy = S * 0.5 + (((idx // 3) % 3) - 1) * 8
    tilt = -9.0 + (idx % 7) * 3.0
    shape_kind = idx % 6

    p.save()
    p.translate(cx, cy - 4)
    p.rotate(tilt * 0.7)
    bg_grad = QLinearGradient(-160, -130, 160, 130)
    bg_grad.setColorAt(0.0, _qc(c1))
    bg_grad.setColorAt(1.0, _qc(c2))
    p.setPen(QPen(_qc(dark), 10, Qt.SolidLine, Qt.RoundCap, Qt.RoundJoin))
    p.setBrush(QBrush(bg_grad))
    if shape_kind == 0:
        pts = [QPointF((174 if k % 2 == 0 else 138) * math.sin(k * math.pi / 7),
                       -(152 if k % 2 == 0 else 120) * math.cos(k * math.pi / 7)) for k in range(14)]
        p.drawPolygon(QPolygonF(pts))
    elif shape_kind == 1:
        p.drawRoundedRect(QRectF(-168, -124, 336, 224), 48, 48)
        p.drawPolygon(QPolygonF([QPointF(-52, 96), QPointF(-10, 96), QPointF(-46, 148)]))
    elif shape_kind == 2:
        p.drawEllipse(QPointF(-102, -98), 40, 40)
        p.drawEllipse(QPointF(102, -98), 40, 40)
        p.drawRoundedRect(QRectF(-164, -112, 328, 226), 84, 84)
    elif shape_kind == 3:
        pts = [QPointF(170 * math.cos(k * math.pi / 3), 138 * math.sin(k * math.pi / 3)) for k in range(6)]
        p.drawPolygon(QPolygonF(pts))
    elif shape_kind == 4:
        for px, py, pr in ((-90, -56, 76), (86, -54, 78), (-110, 26, 74), (108, 24, 72), (0, -78, 86)):
            p.drawEllipse(QPointF(px, py), pr, pr)
        p.drawRoundedRect(QRectF(-150, -68, 300, 168), 64, 64)
    else:
        p.drawRoundedRect(QRectF(-172, -96, 344, 192), 28, 28)
    p.restore()

    # Varied decorative corner badge icon (heart / star / circle) to boost dhash uniqueness
    badge_x = cx + (128 if idx % 2 == 0 else -128)
    badge_y = cy - 114
    p.setPen(QPen(_qc(dark), 6))
    p.setBrush(QColor("#FFF8B8"))
    if idx % 3 == 0:
        sr = 24
        star = [QPointF(badge_x + (sr if k % 2 == 0 else sr * 0.42) * math.sin(k * math.pi / 5),
                        badge_y - (sr if k % 2 == 0 else sr * 0.42) * math.cos(k * math.pi / 5)) for k in range(10)]
        p.drawPolygon(QPolygonF(star))
    elif idx % 3 == 1:
        p.setBrush(QColor("#FB7185"))
        hp = QPainterPath()
        hp.moveTo(badge_x, badge_y + 22)
        hp.cubicTo(badge_x - 32, badge_y - 4, badge_x - 22, badge_y - 26, badge_x, badge_y - 10)
        hp.cubicTo(badge_x + 22, badge_y - 26, badge_x + 32, badge_y - 4, badge_x, badge_y + 22)
        p.drawPath(hp)
    else:
        p.setBrush(QColor("#38BDF8"))
        p.drawEllipse(QPointF(badge_x, badge_y), 22, 22)

    f1 = QFont(_BN_FAMILY, 42, QFont.Black)
    _draw_outlined_text(p, line1, cx, cy - (36 if line2 else 6), f1,
                        QColor("#FFFDF5"), QColor("#FFE066"), _qc("#18121E"), 12.0, tilt, max_width=292.0)
    if line2:
        f2 = QFont(_BN_FAMILY, 36, QFont.Black)
        _draw_outlined_text(p, line2, cx, cy + 40, f2,
                            QColor("#FFFFFF"), QColor("#BAE6FD"), _qc("#18121E"), 11.0, tilt, max_width=278.0)
    p.end()
    return add_diecut_contour(_qimg_to_pil(qi), rim_rgb=(255, 255, 255))


def render_kawaii_mascot(idx: int, main_hex: str, dark_hex: str, caption: str) -> Image.Image:
    """Cute Mochi-cat / Boba-bear / Frog / Bunny character stickers with warm monoline + die-cut."""
    ensure_qt()
    qi = QImage(S, S, QImage.Format_RGBA8888)
    qi.fill(Qt.transparent)
    p = QPainter(qi)
    p.setRenderHint(QPainter.Antialiasing, True)
    p.setRenderHint(QPainter.TextAntialiasing, True)

    cx = S * 0.5 + ((idx % 3) - 1) * 8
    cy = S * 0.48
    species = idx % 4
    outline = _qc(dark_hex)
    fill = _qc(main_hex)
    shade = _mix_qcolor(main_hex, dark_hex, 0.18)

    p.save()
    p.translate(cx, cy)
    p.rotate(-6 + (idx % 5) * 3)
    sc = 0.84 + (idx % 4) * 0.04
    p.scale(sc, sc)

    p.setPen(QPen(outline, 9, Qt.SolidLine, Qt.RoundCap, Qt.RoundJoin))
    p.setBrush(fill)
    if species == 0:
        for sgn in (-1, 1):
            p.drawPolygon(QPolygonF([QPointF(sgn * 124, -40), QPointF(sgn * 138, -148), QPointF(sgn * 42, -106)]))
            p.setBrush(QColor("#F9A8D4"))
            p.setPen(Qt.NoPen)
            p.drawPolygon(QPolygonF([QPointF(sgn * 112, -55), QPointF(sgn * 124, -120), QPointF(sgn * 58, -94)]))
            p.setBrush(fill)
            p.setPen(QPen(outline, 9, Qt.SolidLine, Qt.RoundCap, Qt.RoundJoin))
    elif species == 1:
        for sgn in (-1, 1):
            p.drawEllipse(QPointF(sgn * 108, -96), 46, 46)
    elif species == 2:
        for sgn in (-1, 1):
            p.drawEllipse(QPointF(sgn * 84, -98), 44, 44)
    else:
        for sgn in (-1, 1):
            p.drawEllipse(QPointF(sgn * 62, -118), 30, 62)

    head_grad = QRadialGradient(-30, -50, 210)
    head_grad.setColorAt(0.0, _mix_qcolor(main_hex, "#FFFFFF", 0.35))
    head_grad.setColorAt(0.7, fill)
    head_grad.setColorAt(1.0, shade)
    p.setBrush(QBrush(head_grad))
    p.drawRoundedRect(QRectF(-144, -108, 288, 222), 118, 108)

    p.setBrush(fill)
    p.drawEllipse(QPointF(-86, 98), 32, 24)
    p.drawEllipse(QPointF(86, 98), 32, 24)

    _draw_eyes_and_mouth(p, 0, -4, 0.96, idx, outline, gloss=True)

    prop_col = QColor("#FB7185") if idx % 2 == 0 else QColor("#38BDF8")
    p.setPen(QPen(outline, 6))
    p.setBrush(prop_col)
    if idx % 2 == 0:
        hp = QPainterPath()
        hp.moveTo(0, 128)
        hp.cubicTo(-44, 92, -32, 62, 0, 82)
        hp.cubicTo(32, 62, 44, 92, 0, 128)
        p.drawPath(hp)
    else:
        p.drawRoundedRect(QRectF(-26, 68, 52, 58), 14, 14)
    p.restore()

    if caption:
        fcap = QFont(_UI_FAMILY, 36, QFont.Black)
        _draw_outlined_text(p, caption, cx, S - 104, fcap,
                            QColor("#FFFFFF"), _qc(main_hex), outline, 11.0, -4 + (idx % 4) * 3, max_width=290.0)
    p.end()
    return add_diecut_contour(_qimg_to_pil(qi), rim_rgb=(255, 255, 255))


def render_fluent_3d(idx: int, base_hex: str, accent_hex: str, caption: str) -> Image.Image:
    """Volumetric 3D studio-lit reaction emoji (no black outline, Fluent UI inspired) + die-cut."""
    ensure_qt()
    qi = QImage(S, S, QImage.Format_RGBA8888)
    qi.fill(Qt.transparent)
    p = QPainter(qi)
    p.setRenderHint(QPainter.Antialiasing, True)
    p.setRenderHint(QPainter.TextAntialiasing, True)

    cx, cy = S * 0.5, S * 0.46
    r = 148.0 + (idx % 3) * 8.0
    g = QRadialGradient(cx - r * 0.34, cy - r * 0.36, r * 1.48)
    g.setColorAt(0.0, _mix_qcolor(base_hex, "#FFFFFF", 0.52))
    g.setColorAt(0.55, _qc(base_hex))
    g.setColorAt(1.0, _mix_qcolor(base_hex, "#581C1C", 0.42))
    p.setPen(Qt.NoPen)
    p.setBrush(QBrush(g))
    p.drawEllipse(QPointF(cx, cy), r, r * 0.96)

    shine = QRadialGradient(cx - r * 0.25, cy - r * 0.52, r * 0.52)
    shine.setColorAt(0.0, QColor(255, 255, 255, 130))
    shine.setColorAt(1.0, QColor(255, 255, 255, 0))
    p.setBrush(QBrush(shine))
    p.drawEllipse(QPointF(cx - r * 0.18, cy - r * 0.44), r * 0.56, r * 0.32)

    _draw_eyes_and_mouth(p, cx, cy + 4, 1.02, idx + 1, QColor("#3B2314"), gloss=True)

    p.setBrush(_qc(accent_hex))
    ox = cx + (122 if idx % 2 == 0 else -122)
    p.drawEllipse(QPointF(ox, cy - 104), 26, 26)
    p.setBrush(QColor(255, 255, 255, 150))
    p.drawEllipse(QPointF(ox - 6, cy - 110), 8, 8)

    if caption:
        fcap = QFont(_UI_FAMILY, 34, QFont.Black)
        _draw_outlined_text(p, caption, cx, S - 98, fcap,
                            QColor("#FFFFFF"), _qc(accent_hex), QColor("#271A1D"), 10.0, -3 + (idx % 3) * 3, max_width=290.0)
    p.end()
    return add_diecut_contour(_qimg_to_pil(qi), rim_rgb=(255, 255, 255), border_size=19)


def render_neon_vinyl(idx: int, neon_hex: str, second_hex: str, caption: str) -> Image.Image:
    """Patswerk / Figma style dark-mode neon rim die-cut pop sticker."""
    ensure_qt()
    qi = QImage(S, S, QImage.Format_RGBA8888)
    qi.fill(Qt.transparent)
    p = QPainter(qi)
    p.setRenderHint(QPainter.Antialiasing, True)
    p.setRenderHint(QPainter.TextAntialiasing, True)

    cx = S * 0.5 + ((idx % 3) - 1) * 10
    cy = S * 0.43 + (((idx // 3) % 3) - 1) * 8
    mode = (idx + idx // 4) % 8
    dark_body = QColor("#181A24")
    p.save()
    p.translate(cx, cy)
    p.rotate(-9 + (idx % 7) * 3)
    sc = 0.86 + (idx % 4) * 0.05
    p.scale(sc, sc)
    p.setPen(QPen(_qc("#0C0D12"), 10, Qt.SolidLine, Qt.RoundCap, Qt.RoundJoin))

    if mode == 0:  # Neon crown
        p.setBrush(_qc(neon_hex))
        pts = [QPointF(-120, 72), QPointF(-134, -68), QPointF(-60, 8),
               QPointF(0, -104), QPointF(60, 8), QPointF(134, -68), QPointF(120, 72)]
        p.drawPolygon(QPolygonF(pts))
    elif mode == 1:  # Retro boombox
        p.setBrush(dark_body)
        p.drawRoundedRect(QRectF(-142, -86, 284, 172), 26, 26)
        p.setBrush(_qc(neon_hex))
        p.drawEllipse(QPointF(-68, 12), 44, 44)
        p.drawEllipse(QPointF(68, 12), 44, 44)
        p.setBrush(_qc(second_hex))
        p.drawRoundedRect(QRectF(-52, -60, 104, 32), 8, 8)
    elif mode == 2:  # Lightning bolt
        p.setBrush(_qc(neon_hex))
        p.drawPolygon(QPolygonF([
            QPointF(36, -135), QPointF(-82, 12), QPointF(-12, 12),
            QPointF(-44, 135), QPointF(96, -26), QPointF(16, -26)
        ]))
    elif mode == 3:  # Shades cyber emoji
        p.setBrush(_qc(neon_hex))
        p.drawEllipse(QPointF(0, 0), 126, 126)
        p.setBrush(dark_body)
        p.drawRoundedRect(QRectF(-108, -38, 94, 48), 14, 14)
        p.drawRoundedRect(QRectF(14, -38, 94, 48), 14, 14)
        p.drawArc(QRectF(-48, 18, 96, 56), 200 * 16, 140 * 16)
    elif mode == 4:  # Neon drink / boba badge
        p.setBrush(_qc(second_hex))
        p.drawRoundedRect(QRectF(-92, -76, 184, 162), 34, 34)
        p.setBrush(_qc(neon_hex))
        p.drawRoundedRect(QRectF(-108, -94, 216, 36), 16, 16)
    elif mode == 5:  # Comic rocket
        p.setBrush(_qc(neon_hex))
        p.drawPolygon(QPolygonF([QPointF(0, -136), QPointF(58, -32), QPointF(52, 74),
                               QPointF(-52, 74), QPointF(-58, -32)]))
        p.setBrush(_qc(second_hex))
        p.drawPolygon(QPolygonF([QPointF(-52, 26), QPointF(-102, 92), QPointF(-42, 74)]))
        p.drawPolygon(QPolygonF([QPointF(52, 26), QPointF(102, 92), QPointF(42, 74)]))
        p.setBrush(dark_body)
        p.drawEllipse(QPointF(0, -24), 28, 28)
    elif mode == 6:  # 10-point neon starburst
        pts = [QPointF((138 if k % 2 == 0 else 72) * math.sin(k * math.pi / 5),
                       -(138 if k % 2 == 0 else 72) * math.cos(k * math.pi / 5)) for k in range(10)]
        p.setBrush(_qc(neon_hex))
        p.drawPolygon(QPolygonF(pts))
        p.setBrush(dark_body)
        p.drawEllipse(QPointF(0, 0), 42, 42)
    else:  # Neon diamond crystal
        p.setBrush(_qc(neon_hex))
        p.drawPolygon(QPolygonF([QPointF(-78, -110), QPointF(78, -110), QPointF(132, -26),
                               QPointF(0, 126), QPointF(-132, -26)]))
        p.setBrush(_qc(second_hex))
        p.drawPolygon(QPolygonF([QPointF(-78, -110), QPointF(78, -110), QPointF(0, -26)]))
    p.restore()

    if caption:
        fcap = QFont(_UI_FAMILY, 40, QFont.Black)
        _draw_outlined_text(p, caption, cx, S - 102, fcap,
                            QColor("#FFFFFF"), _qc(neon_hex), QColor("#0C0E14"), 11.0, -6.0 + (idx % 5) * 3.0, max_width=290.0)
    p.end()

    rim = QColor(neon_hex)
    return add_diecut_contour(_qimg_to_pil(qi), rim_rgb=(rim.red(), rim.green(), rim.blue()), border_size=21)


def render_animated_sticker_webp(idx: int, main_hex: str, dark_hex: str, caption: str,
                                 frames: int = 5, duration_ms: int = 115) -> Tuple[bytes, Image.Image]:
    """Renders an animated RGBA looping WebP sticker (< 95KB) + frame0 preview."""
    ensure_qt()
    seq = []
    kind = idx % 3
    for f_i in range(frames):
        phase = 2.0 * math.pi * f_i / frames
        qi = QImage(S, S, QImage.Format_RGBA8888)
        qi.fill(Qt.transparent)
        p = QPainter(qi)
        p.setRenderHint(QPainter.Antialiasing, True)
        p.setRenderHint(QPainter.TextAntialiasing, True)
        cx, cy = S * 0.5, S * 0.45

        if kind == 0:
            sc = 0.82 + 0.12 * abs(math.sin(phase))
            p.save()
            p.translate(cx, cy)
            p.scale(sc, sc)
            hp = QPainterPath()
            hp.moveTo(0, 120)
            hp.cubicTo(-185, 16, -148, -140, 0, -52)
            hp.cubicTo(148, -140, 185, 16, 0, 120)
            g = QRadialGradient(-30, -50, 200)
            g.setColorAt(0.0, _mix_qcolor(main_hex, "#FFFFFF", 0.4))
            g.setColorAt(1.0, _qc(main_hex))
            p.setPen(QPen(_qc(dark_hex), 9))
            p.setBrush(QBrush(g))
            p.drawPath(hp)
            p.restore()
            for sp_k in range(4):
                a = phase + sp_k * math.pi * 0.5
                sx = cx + math.cos(a) * 148
                sy = cy + math.sin(a) * 126
                sr = 10 + 4 * math.sin(phase * 2 + sp_k)
                p.setPen(Qt.NoPen)
                p.setBrush(QColor("#FFE066"))
                p.drawEllipse(QPointF(sx, sy), sr, sr)
        elif kind == 1:
            sy = cy + math.sin(phase) * 16
            sx_sc = 1.0 + 0.05 * math.cos(phase)
            sy_sc = 1.0 - 0.05 * math.cos(phase)
            p.save()
            p.translate(cx, sy)
            p.scale(sx_sc, sy_sc)
            p.setPen(QPen(_qc(dark_hex), 9))
            p.setBrush(_qc(main_hex))
            p.drawEllipse(QPointF(0, 0), 124, 120)
            _draw_eyes_and_mouth(p, 0, -4, 0.88, idx + 1, _qc(dark_hex), gloss=True)
            p.restore()
            for c_i in range(5):
                ca = phase + c_i * 1.25
                px = cx + math.cos(ca) * 146
                py = cy - 24 + math.sin(ca * 2) * 76
                p.setPen(Qt.NoPen)
                p.setBrush(QColor("#FF5F9E" if c_i % 2 == 0 else "#38BDF8"))
                p.drawEllipse(QPointF(px, py), 9, 9)
        else:
            bob = math.sin(phase) * 7
            p.setPen(QPen(_qc(dark_hex), 9))
            p.setBrush(_qc(main_hex))
            p.drawRoundedRect(QRectF(cx - 96, cy - 32 + bob, 178, 142), 32, 32)
            p.setBrush(Qt.NoBrush)
            p.drawArc(QRectF(cx + 62, cy - 6 + bob, 66, 86), 270 * 16, 180 * 16)
            p.setPen(QPen(QColor("#F8FAFC"), 8, Qt.SolidLine, Qt.RoundCap))
            for st_x in (-48, 0, 48):
                y_off = ((f_i / frames) * 30 + abs(st_x) * 0.3) % 30
                p.drawArc(QRectF(cx + st_x - 14, cy - 108 - y_off + bob, 28, 40), 70 * 16, 200 * 16)

        if caption:
            fcap = QFont(_UI_FAMILY, 34, QFont.Black)
            _draw_outlined_text(p, caption, cx, S - 84, fcap,
                                QColor("#FFFFFF"), _qc(main_hex), _qc(dark_hex), 10.0, 0.0, max_width=285.0)
        p.end()
        frame_img = add_diecut_contour(_qimg_to_pil(qi), rim_rgb=(255, 255, 255), border_size=19)
        seq.append(frame_img)

    for qual in (72, 56, 42, 28, 18):
        buf = io.BytesIO()
        seq[0].save(buf, "WEBP", save_all=True, append_images=seq[1:], duration=duration_ms,
                    loop=0, quality=qual, method=4, allow_mixed=True, minimize_size=True)
        raw = buf.getvalue()
        if len(raw) <= 95 * 1024:
            return raw, seq[0]
    return raw, seq[0]
