import { getDownloadURL, ref, uploadBytesResumable } from 'firebase/storage';
import { storage } from '../firebase';

const MAX_IMAGE_DIMENSION = 1600;
const JPEG_QUALITY = 0.85;

export async function compressImageToJpeg(file: File): Promise<Blob> {
  if (typeof createImageBitmap === 'undefined') {
    return file;
  }
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1, MAX_IMAGE_DIMENSION / Math.max(bitmap.width, bitmap.height));
  const targetWidth = Math.max(1, Math.round(bitmap.width * scale));
  const targetHeight = Math.max(1, Math.round(bitmap.height * scale));

  const canvas = document.createElement('canvas');
  canvas.width = targetWidth;
  canvas.height = targetHeight;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    bitmap.close();
    return file;
  }
  ctx.drawImage(bitmap, 0, 0, targetWidth, targetHeight);
  bitmap.close();

  return await new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (blob) resolve(blob);
        else reject(new Error('Failed to compress selected image.'));
      },
      'image/jpeg',
      JPEG_QUALITY
    );
  });
}

export async function uploadChatImage(
  conversationId: string,
  senderId: string,
  file: File,
  onProgress?: (fraction: number) => void
): Promise<string> {
  const blob = await compressImageToJpeg(file);
  if (blob.size > 9.5 * 1024 * 1024) {
    throw new Error('Compressed image exceeds the 10 MB upload limit.');
  }
  const fileName = `img_${Date.now()}_${Math.random().toString(36).slice(2, 8)}.jpg`;
  const storageRef = ref(storage, `chat_media/${conversationId}/${senderId}/${fileName}`);
  const task = uploadBytesResumable(storageRef, blob, { contentType: 'image/jpeg' });

  return await new Promise<string>((resolve, reject) => {
    task.on(
      'state_changed',
      (snapshot) => {
        if (snapshot.totalBytes > 0) {
          onProgress?.(snapshot.bytesTransferred / snapshot.totalBytes);
        }
      },
      (error) => reject(error),
      async () => {
        try {
          const url = await getDownloadURL(task.snapshot.ref);
          resolve(url);
        } catch (err) {
          reject(err);
        }
      }
    );
  });
}

export async function uploadChatVoiceNote(
  conversationId: string,
  senderId: string,
  blob: Blob,
  onProgress?: (fraction: number) => void
): Promise<string> {
  if (blob.size > 9.5 * 1024 * 1024) {
    throw new Error('Voice note exceeds the 10 MB upload limit.');
  }
  const ext = blob.type.includes('mp4') ? 'm4a' : 'webm';
  const fileName = `voice_${Date.now()}_${Math.random().toString(36).slice(2, 8)}.${ext}`;
  const storageRef = ref(storage, `chat_media/${conversationId}/${senderId}/${fileName}`);
  const contentType = blob.type || 'audio/webm';
  const task = uploadBytesResumable(storageRef, blob, { contentType });

  return await new Promise<string>((resolve, reject) => {
    task.on(
      'state_changed',
      (snapshot) => {
        if (snapshot.totalBytes > 0) {
          onProgress?.(snapshot.bytesTransferred / snapshot.totalBytes);
        }
      },
      (error) => reject(error),
      async () => {
        try {
          const url = await getDownloadURL(task.snapshot.ref);
          resolve(url);
        } catch (err) {
          reject(err);
        }
      }
    );
  });
}

export function formatDurationMs(ms?: number | null): string {
  const totalSec = Math.max(0, Math.round((ms ?? 0) / 1000));
  const m = Math.floor(totalSec / 60);
  const s = totalSec % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
}
