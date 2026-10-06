import qrcode
from PIL import Image, ImageDraw, ImageFont

# ==================================================
# CARD SETTINGS
# ==================================================

DPI = 300

# Physical card size: 63 x 88 mm
CARD_WIDTH_MM = 63
CARD_HEIGHT_MM = 86

# Convert mm -> pixels
CARD_WIDTH_PX = round(CARD_WIDTH_MM / 25.4 * DPI)
CARD_HEIGHT_PX = round(CARD_HEIGHT_MM / 25.4 * DPI)

background_color = "#000000"
white = "#FFFFFF"
pink = "#f50c6f"


# --------------------------------------------------
# PATHS
# --------------------------------------------------

hitster_image_path = "../game-pic/game2.png"
second_image_path = "../albums/The_Weeknd-Blinding_Lights.png"


# ==================================================
# CREATE 63 x 88 MM CANVAS
# ==================================================

canvas = Image.new(
    "RGB",
    (CARD_WIDTH_MM, CARD_HEIGHT_PX),
    background_color
)

draw = ImageDraw.Draw(canvas)


# --------------------------------------------------
# LOAD AMINA'S HITSTER IMAGE
# --------------------------------------------------

hitster_image = Image.open(
    hitster_image_path
).convert("RGBA"
)

# Maximum size for the top image
max_width = CARD_WIDTH_PX - 20
max_height = 320

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

top_line_y = 330

draw.line(
    (40, top_line_y, CARD_WIDTH_PX - 40, top_line_y),
    fill=pink,
    width=4
)

# ==================================================
# 2. SECOND IMAGE
# ==================================================

second_image = Image.open(
    second_image_path
).convert("RGBA")

# Maximum size for second image
second_max_width = CARD_WIDTH_PX - 100
second_max_height = 500

second_image.thumbnail(
    (second_max_width, second_max_height),
    Image.Resampling.LANCZOS
)

# Center horizontally
x = (CARD_WIDTH_PX - second_image.width) // 2

# Position below AMINA'S HITSTER
y = 360

canvas.paste(
    second_image,
    (x, y),
    second_image
)

# --------------------------------------------------
# BOTTOM SEPARATOR
# --------------------------------------------------

bottom_line_y = 880

draw.line(
    (40, bottom_line_y, CARD_WIDTH_PX - 40, bottom_line_y),
    fill=pink,
    width=4
)

font = ImageFont.truetype(
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    30
)

release_date = "2020"
song = f"Artist: The Weeknd\nSong: Blinding Lights\nRelease date: {release_date}"

bbox = draw.textbbox(
    (0, 0),
    song,
    font=font
)
text_width = bbox[2] - bbox[0]

x_song = 42
y_song = bottom_line_y + 17

draw.text(
    (x_song, y_song),
    song,
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
    "../years/2020/back/The_Weeknd-Blinding_Lights.png",
    dpi=(DPI, DPI)
)

print(
    f"Card size: {CARD_WIDTH_PX} x {CARD_HEIGHT_PX} px "
    f"({CARD_WIDTH_MM} x {CARD_HEIGHT_MM} mm at {DPI} DPI)"
)