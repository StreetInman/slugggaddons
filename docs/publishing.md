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
2. **Proxy.** Create a free [Cloudflare](https://dash.cloudflare.com/) account. Deploy the Worker in
   `proxy/` (`npx wrangler deploy`, then `npx wrangler secret put HYPIXEL_KEY`; it adds the key to requests to `/v2/skyblock/*` and caches responses for a few
   minutes so many players viewing the same profile cost one request), and store the key as a
   Worker secret, never in code. Then the release build's `apiBaseUrl` is set to the Worker's URL
   and `apiKey` is left empty.
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

## Renaming the GitHub repo (optional)

The repo was renamed from `skyblock-pv` to `slugggaddons`. To rename it again, open the repo's
**Settings → General → Repository name**. GitHub redirects the old URL, so nothing else breaks.
