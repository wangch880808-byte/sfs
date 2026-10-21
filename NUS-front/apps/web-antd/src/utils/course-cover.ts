const COURSE_COVER_BASE_URL = '/static/imgs/course-covers';
const FIRST_COURSE_COVER_FILE = '21d8e7bf2a3e43a975033ded3a8c6cdb.jpg';

export const defaultCourseCoverUrls = [
  FIRST_COURSE_COVER_FILE,
  '460de6839a18243587ab07561a532a9f.jpg',
  '54f2afbda14bf0ecdaaf5888f6c1ec20.jpg',
  '669ca80d6a0c5f74835bb936a41aabca.jpg',
  '6ffba3b987386ac0b1493280e29536fb.jpg',
  '75a373f4604571af4b37bd57e8177b3b.jpg',
  '883735c8bbf5a4c61381f1ed96b40c9d.jpg',
  '8a0d7e35ecbb20ec4b19f185eed0bc2b.jpg',
  '8c9ec6b14aa72c81c375091ce79cf7cb.jpg',
  '8f3d6d1e7b9d26d34ae28a8e3b253265.jpg',
  '95d9a5028f324f54c01f505fb2f41087.jpg',
  '987728a81491be987fa29edd08519ba5.jpg',
  'a597b7c95a3e72dbbdb21f17011ce85f.jpg',
  'a83f7a04a1ebec7b608e52bdb96b4f5b.jpg',
  'aefed707926791941a844fb718b43955.jpg',
  'b1ac7d7a5a8c620e48307876cd6819e9.jpg',
  'b7b9a80175b2d80938d72fcbfdabce24.jpg',
  'b96809be433f26b4e9a9966be56aa31c.jpg',
  'bfb54835f338f86191b4ab6fdc364215.jpg',
  'd86d681b1596f9cebb6b5b0de08a323d.jpg',
  'ebe4b550f689a518631aa3f61a36c10e.jpg',
  'ee1c91e5d692ad719f7edd5c90a3625e.jpg',
  'f23a4b313e1aa0a7a840ab81aabc918f.jpg',
  'f531b4fc974f76501cb5779964b2ff6f.jpg',
].map((fileName) => `${COURSE_COVER_BASE_URL}/${fileName}`);

function hashIdentity(identity?: number | string) {
  const value = String(identity ?? '').trim();
  if (!value) return 0;

  let hash = 0;
  for (let i = 0; i < value.length; i += 1) {
    hash = (hash * 31 + (value.codePointAt(i) ?? 0)) >>> 0;
  }
  return hash;
}

export function getDefaultCourseCoverUrl(identity?: number | string) {
  return (
    defaultCourseCoverUrls[
      hashIdentity(identity) % defaultCourseCoverUrls.length
    ] ?? `${COURSE_COVER_BASE_URL}/${FIRST_COURSE_COVER_FILE}`
  );
}

export function getCourseCoverUrl(
  coverUrl?: null | string,
  identity?: number | string,
) {
  const normalized = coverUrl?.trim();
  return normalized || getDefaultCourseCoverUrl(identity);
}

export function applyCourseCoverFallback(
  event: Event,
  identity?: number | string,
) {
  const image = event.target as HTMLImageElement | null;
  if (!image) return;

  const fallbackUrl = getDefaultCourseCoverUrl(identity);
  if (image.getAttribute('src') === fallbackUrl) return;

  image.src = fallbackUrl;
}
