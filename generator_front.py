import qrcode
from PIL import Image, ImageDraw, ImageFont

url = "https://open.spotify.com/track/3QuRLv8zkIYH31O5VgEpmo?autoplay_ok=1"

# --------------------------------------------------
# PRINT SETTINGS
# --------------------------------------------------

DPI = 300

CARD_WIDTH_MM = 60
CARD_HEIGHT_MM = 90

CARD_WIDTH_PX = round(CARD_WIDTH_MM / 25.4 * DPI)
CARD_HEIGHT_PX = round(CARD_HEIGHT_MM / 25.4 * DPI)

background_color = "#000000"
white = "#FFFFFF"
pink = "#f50c6f"

# --------------------------------------------------
# PATHS
# --------------------------------------------------

hitster_image_path = "game-pic/game2.png"
spotify_logo_path = "logo/spotify_logo_without_background.png"


# --------------------------------------------------
# CREATE QR CODE
# --------------------------------------------------

qr = qrcode.QRCode(
    version=5,
    error_correction=qrcode.constants.ERROR_CORRECT_H,
    box_size=10,
    border=4,
)

qr.add_data(url)
qr.make(fit=True)

qr_image = qr.make_image(
    fill_color=white,
    back_color=background_color
).convert("RGB")


# --------------------------------------------------
# RESIZE QR CODE
# --------------------------------------------------

# Leave some space around the QR code
qr_size = 540

qr_image = qr_image.resize(
    (qr_size, qr_size),
    Image.Resampling.NEAREST
)


# --------------------------------------------------
# ADD SPOTIFY LOGO
# --------------------------------------------------

logo = Image.open(
    spotify_logo_path
).convert("RGBA")

logo_size = 100
logo.thumbnail((logo_size, logo_size))

x = (qr_image.width - logo.width) // 2
y = (qr_image.height - logo.height) // 2

qr_image.paste(
    logo,
    (x, y),
    logo
)


# --------------------------------------------------
# LOAD AMINA'S HITSTER IMAGE
# --------------------------------------------------

hitster_image = Image.open(
    hitster_image_path
).convert("RGBA"
)

# Maximum size for the top image
max_width = CARD_WIDTH_PX - 20
max_height = 340

hitster_image.thumbnail(
    (max_width, max_height),
    Image.Resampling.LANCZOS
)


# --------------------------------------------------
# CREATE 60 x 90 MM CANVAS
# --------------------------------------------------

canvas = Image.new(
    "RGB",
    (
        CARD_WIDTH_PX,
        CARD_HEIGHT_PX
    ),
    background_color
)

draw = ImageDraw.Draw(canvas)


# --------------------------------------------------
# PLACE AMINA'S HITSTER IMAGE
# --------------------------------------------------

x = (CARD_WIDTH_PX - hitster_image.width) // 2
y = 10

canvas.paste(
    hitster_image,
    (x, y),
    hitster_image
)


# --------------------------------------------------
# TOP SEPARATOR
# --------------------------------------------------

top_line_y = 350

draw.line(
    (40, top_line_y, CARD_WIDTH_PX - 40, top_line_y),
    fill=pink,
    width=4
)

# --------------------------------------------------
# PLACE QR CODE
# --------------------------------------------------

qr_x = (CARD_WIDTH_PX - qr_image.width) // 2
qr_y = top_line_y + 25

canvas.paste(
    qr_image,
    (qr_x, qr_y)
)


# --------------------------------------------------
# BOTTOM SEPARATOR
# --------------------------------------------------

bottom_line_y = qr_y + qr_image.height + 25

draw.line(
    (40, bottom_line_y, CARD_WIDTH_PX - 40, bottom_line_y),
    fill=pink,
    width=4
)


# --------------------------------------------------
# SCAN ME!
# --------------------------------------------------

font = ImageFont.truetype(
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    45
)

text = "Scan me!"

bbox = draw.textbbox(
    (0, 0),
    text,
    font=font
)

text_width = bbox[2] - bbox[0]

x = (CARD_WIDTH_PX - text_width) // 2
y = bottom_line_y + 20

draw.text(
    (x, y),
    text,
    fill=white,
    font=font
)


# --------------------------------------------------
# WHITE BORDER
# --------------------------------------------------

border_width = 6

draw.rectangle(
    (
        border_width // 2,
        border_width // 2,
        CARD_WIDTH_PX - border_width // 2 - 1,
        CARD_HEIGHT_PX - border_width // 2 - 1
    ),
    outline=white,
    width=border_width
)


# --------------------------------------------------
# SAVE FOR PRINTING
# --------------------------------------------------

canvas.save(
    "qr-codes/Harry_Styles-Carlas_Song.png",
    dpi=(DPI, DPI)
)

print(
    f"Card size: {CARD_WIDTH_PX} x {CARD_HEIGHT_PX} px "
    f"({CARD_WIDTH_MM} x {CARD_HEIGHT_MM} mm at {DPI} DPI)"
)