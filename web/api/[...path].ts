import { proxyRequest } from './proxy';

type RouteContext = {
  params?: {
    path?: string | string[];
  };
};

function decodePathSegment(segment: string): string {
  try {
    return decodeURIComponent(segment);
  } catch {
    // Keep malformed escapes opaque; proxyRequest will safely encode them
    // instead of allowing them to alter the upstream URL.
    return segment;
  }
}

function pathFromRequest(request: Request): string[] {
  const pathname = new URL(request.url).pathname;
  const relative = pathname.replace(/^\/api(?:\/|$)/, '');
  return relative
    .split('/')
    .filter((segment) => segment.length > 0)
    .map(decodePathSegment);
}

function routePath(request: Request, context?: RouteContext): string[] {
  const routeParam = context?.params?.path;
  if (routeParam === undefined) return pathFromRequest(request);
  return Array.isArray(routeParam) ? routeParam : [routeParam];
}

/** Vercel Web Runtime catch-all for the relative /api/* surface. */
export default function handler(request: Request, context?: RouteContext): Promise<Response> {
  return proxyRequest(request, routePath(request, context));
}
