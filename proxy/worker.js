// SlugggAddons API proxy (Cloudflare Worker). Holds the Hypixel production key so it never ships
// inside the mod, and caches responses so many players viewing the same profile cost one request.
//
// Deploy: `npx wrangler deploy` from this folder, then `npx wrangler secret put HYPIXEL_KEY`.

const ALLOWED = [/^\/v2\/skyblock\/profiles$/, /^\/v2\/skyblock\/bazaar$/, /^\/v2\/resources\/skyblock\/[a-z_]+$/];
const CACHE_SECONDS = 300;

export default {
  async fetch(request, env, ctx) {
    if (request.method !== "GET") return new Response("Method not allowed", { status: 405 });
    const url = new URL(request.url);
    if (!ALLOWED.some((re) => re.test(url.pathname))) return new Response("Not found", { status: 404 });
    if (url.pathname === "/v2/skyblock/profiles" && !/^[0-9a-f-]{32,36}$/i.test(url.searchParams.get("uuid") || "")) {
      return new Response("Bad uuid", { status: 400 });
    }

    const target = new URL("https://api.hypixel.net" + url.pathname);
    if (url.searchParams.has("uuid")) target.searchParams.set("uuid", url.searchParams.get("uuid").replace(/-/g, ""));

    const cache = caches.default;
    const cacheKey = new Request(target.toString());
    const cached = await cache.match(cacheKey);
    if (cached) return cached;

    const upstream = await fetch(target, { headers: { "API-Key": env.HYPIXEL_KEY, "User-Agent": "slugggaddons-proxy" } });
    const response = new Response(upstream.body, upstream);
    response.headers.set("Cache-Control", `public, max-age=${CACHE_SECONDS}`);
    if (upstream.ok) ctx.waitUntil(cache.put(cacheKey, response.clone()));
    return response;
  },
};
