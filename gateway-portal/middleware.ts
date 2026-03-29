import { NextRequest, NextResponse } from 'next/server';

/** Paths that require authentication (route groups using (authenticated)) */
const PROTECTED_PREFIX = '/(authenticated)';

/** Path prefixes that are always public */
const PUBLIC_PATHS = ['/', '/catalog', '/auth'];

function isPublicPath(pathname: string): boolean {
  if (pathname === '/') return true;
  return PUBLIC_PATHS.some(
    (prefix) => prefix !== '/' && (pathname === prefix || pathname.startsWith(prefix + '/'))
  );
}

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Static assets and Next.js internals — skip
  if (
    pathname.startsWith('/_next') ||
    pathname.startsWith('/api') ||
    pathname.includes('.') // static files (favicon, images, etc.)
  ) {
    return NextResponse.next();
  }

  // Public paths — allow through
  if (isPublicPath(pathname)) {
    return NextResponse.next();
  }

  // For all other paths (dashboard, settings, etc.), check for auth token.
  // In Next.js App Router, route groups like (authenticated) are stripped from the URL,
  // so /dashboard maps to app/(authenticated)/dashboard/page.tsx.
  // We protect anything that is NOT explicitly public.
  const token = request.cookies.get('token')?.value;

  if (!token) {
    const loginUrl = new URL('/auth/login', request.url);
    loginUrl.searchParams.set('redirect', pathname);
    return NextResponse.redirect(loginUrl);
  }

  // Token exists — allow through (server-side validation happens at the API layer)
  return NextResponse.next();
}

export const config = {
  matcher: [
    /*
     * Match all request paths except:
     * - _next/static (static files)
     * - _next/image (image optimization)
     * - favicon.ico
     */
    '/((?!_next/static|_next/image|favicon.ico).*)',
  ],
};
