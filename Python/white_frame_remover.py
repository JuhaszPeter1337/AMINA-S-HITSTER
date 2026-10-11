from pathlib import Path
from PIL import Image

input_folder = Path("../test/front")
output_folder = Path("../test/front_fixed")
output_folder.mkdir(parents=True, exist_ok=True)

# Separate scaling for horizontal and vertical directions
scale_x = 0.96
scale_y = 0.98

dpi = 300

for image_path in input_folder.glob("*.png"):
    img = Image.open(image_path).convert("RGB")
    w, h = img.size

    new_w = int(w * scale_x)
    new_h = int(h * scale_y)

    resized = img.resize(
        (new_w, new_h),
        Image.Resampling.LANCZOS
    )

    canvas = Image.new("RGB", (w, h), "#000000")

    x = (w - new_w) // 2
    y = (h - new_h) // 2

    canvas.paste(resized, (x, y))

    output_path = output_folder / image_path.name
    canvas.save(output_path, dpi=(dpi, dpi))

    print(f"Saved: {output_path}")