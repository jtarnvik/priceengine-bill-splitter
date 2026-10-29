/** The most the current models use, so nothing the model could read is lost: a long receipt has small print. */
const MAX_LONG_EDGE = 2576;
const JPEG_QUALITY = 0.85;

/**
 * The chosen photo, downscaled to at most 2576 px on the long edge and re-encoded as JPEG. One step answers four
 * problems: a phone photo is several MB, iPhones shoot HEIC, which the API does not accept, the canvas drops EXIF
 * (GPS included), and a small file uploads quickly over mobile data at the table.
 *
 * Drawn from an `<img>` onto a canvas of the target size, never one of the photo's own size: iOS limits canvas
 * area, and a 48 MP photo exceeds it. The browser applies the EXIF orientation when it decodes the image, so the
 * width and height are already the right way round.
 *
 * @throws Error with a Swedish message when the browser cannot read the file as an image
 */
export async function downscaleToJpeg(file: File): Promise<Blob> {
  const url = URL.createObjectURL(file);
  try {
    const image = new Image();
    image.src = url;
    try {
      await image.decode();
    } catch {
      throw new Error('Bilden kunde inte läsas. Välj en annan bild.');
    }
    const scale = Math.min(1, MAX_LONG_EDGE / Math.max(image.naturalWidth, image.naturalHeight));
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(image.naturalWidth * scale);
    canvas.height = Math.round(image.naturalHeight * scale);
    const context = canvas.getContext('2d');
    if (!context) {
      throw new Error('Bilden kunde inte förminskas i den här webbläsaren.');
    }
    context.drawImage(image, 0, 0, canvas.width, canvas.height);
    return await canvasToJpeg(canvas);
  } finally {
    URL.revokeObjectURL(url);
  }
}

function canvasToJpeg(canvas: HTMLCanvasElement): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) {
        resolve(blob);
      } else {
        reject(new Error('Bilden kunde inte förminskas i den här webbläsaren.'));
      }
    }, 'image/jpeg', JPEG_QUALITY);
  });
}
