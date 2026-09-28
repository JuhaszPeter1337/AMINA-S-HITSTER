from PIL import Image

img = Image.open("../qr-codes/Harry_Styles-Adore_You.png")

# Convert RGB → CMYK
cmyk = img.convert("CMYK")

# Save as TIFF
cmyk.save("../CMYK/Harry_Styles-Adore_You-front.tif", dpi=(300, 300))