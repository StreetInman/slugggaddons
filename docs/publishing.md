# Publishing

## GitHub releases

Releases are built by `.github/workflows/release.yml`. Pushing a tag such as `v0.2.0` (or pushing
to the `release` branch, which tags the release `v<mod_version>`) builds one jar
per Minecraft version in `versions/`, attaches them all to a GitHub release, and (once set up below)
uploads them to Modrinth.

```sh
git tag v0.2.0
git push origin v0.2.0
```

Bump `mod_version` in `gradle.properties` first so the jar names match the tag.

## Stop replacing your API key (personal key)

Development keys expire every few days. For a mod only you use, apply for a **Personal API key**
instead:

1. Sign in at [developer.hypixel.net](https://developer.hypixel.net/) with your Minecraft account.
2. Create an application, choose the personal key type, and describe it as a client-side profile
   viewer for your own use.
3. Once it's approved, paste the key into `apiKey` in `config/slugggaddons.json`. It doesn't expire.

Keep using a development key until then. Never share either kind of key or put it in a jar.

## Publishing for other players

Other players can't be given your key, so a public release needs a production key held by a small
server ("proxy") that the mod talks to instead of Hypixel.

1. **Production key.** On the developer dashboard, create an application and apply for a production
   key. Describe SlugggAddons as a client-side SkyBlock profile viewer whose requests go through
   your own caching proxy, and link the GitHub repo.
2. **Proxy.** Set up the Cloudflare Worker below and put the production key in it. Then the
   release build's `apiBaseUrl` is set to the Worker's URL and `apiKey` is left empty.
3. **Modrinth project.** Sign in at [modrinth.com](https://modrinth.com) and choose **Create a
   project** → *Mod*. Suggested values: name *SlugggAddons*, URL `slugggaddons`, license MIT,
   **client side: required, server side: unsupported**, loader Fabric, category *Utility*. Link the
   GitHub repo as the source and issue tracker.
4. **Connect releases.** On the project page open **Settings**, copy the **Project ID**, and put it
   in `gradle.properties` as `modrinth_id=`. In your Modrinth account go to **Settings → Personal
   access tokens**, create a token with the *Create versions* scope, and add it to the GitHub repo
   under **Settings → Secrets and variables → Actions** as `MODRINTH_TOKEN`.
5. **Release.** From then on every release (a `v*` tag or a push to the `release` branch) also
   uploads each jar to Modrinth as its own version.

Modrinth reviews a new project before it goes public, which usually takes a few days.

## Setting up the proxy on Cloudflare

The proxy is a tiny free Cloudflare Worker (the code is `proxy/worker.js`). The mod asks the Worker
instead of Hypixel; the Worker adds your key, asks Hypixel, and remembers each answer for 5 minutes.
Your key lives only in Cloudflare, never in the mod. It only passes on profile, bazaar and
resource requests, so nobody can use it to reach other parts of the Hypixel API.

This works with any key. A personal or development key is fine for trying it yourself; use the
production key once other players use the mod. Either way, when a key changes you only update it
in Cloudflare, not in every player's config.

You don't need to install anything; everything is done on the Cloudflare website.

1. **Make an account.** Go to [dash.cloudflare.com/sign-up](https://dash.cloudflare.com/sign-up),
   sign up with your email, and confirm it. You don't need a domain or a credit card.
2. **Create the Worker.** In the left menu open **Compute (Workers)** → **Workers & Pages**, press
   **Create**, choose **Start with Hello World!**, and name it `slugggaddons-proxy`. Press
   **Deploy**. (The first time, Cloudflare asks you to pick a `workers.dev` subdomain; any name works.)
3. **Paste the code.** On the Worker's page press **Edit code**. Delete everything in `worker.js`,
   paste the whole contents of [`proxy/worker.js`](../proxy/worker.js), and press **Deploy** (top
   right).
4. **Add your key.** Go back to the Worker's page → **Settings** → **Variables and Secrets** →
   **Add**. Type: **Secret**. Variable name: `HYPIXEL_KEY` (exactly this). Value: your Hypixel API
   key. Press **Deploy** (or **Save**).
5. **Copy the Worker's address.** It's shown on the Worker's page under **Domains**, and looks like
   `https://slugggaddons-proxy.<your-subdomain>.workers.dev`.
6. **Check it works.** Open `https://slugggaddons-proxy.<your-subdomain>.workers.dev/v2/resources/skyblock/skills`
   in your browser: you should see a page of JSON starting with `{"success":true`. Then try a profile:
   `.../v2/skyblock/profiles?uuid=<a player's UUID>` (find UUIDs on [namemc.com](https://namemc.com)).
   If that says `Invalid API key`, the secret in step 4 is missing or misspelled.
7. **Point the mod at it.** Close Minecraft, open `config/slugggaddons.json`, and set:

   ```json
   "apiKey": "",
   "apiBaseUrl": "https://slugggaddons-proxy.<your-subdomain>.workers.dev",
   ```

   Start the game and use `/pv`. It now goes through your Worker.

**Changing the key later:** Worker → **Settings** → **Variables and Secrets** → edit `HYPIXEL_KEY`
→ paste the new key → **Deploy**. Players don't need to do anything.

**Updating the code later** (if `proxy/worker.js` changes): repeat step 3.

**Limits:** the free plan allows 100,000 requests a day, far more than a profile viewer needs.
The Hypixel key's own rate limit still applies, and caching keeps you well under it.

**Command line instead (optional):** with [Node.js](https://nodejs.org) installed, run
`npx wrangler login`, then `npx wrangler deploy` and `npx wrangler secret put HYPIXEL_KEY` inside the
`proxy/` folder. This does steps 2 to 4 in one go.

## Renaming the GitHub repo (optional)

The repo was renamed from `skyblock-pv` to `slugggaddons`. To rename it again, open the repo's
**Settings → General → Repository name**. GitHub redirects the old URL, so nothing else breaks.
