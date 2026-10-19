# ZidnyKeyboard translation server

A Cloudflare Worker that translates text for the keyboard with Workers AI (`@cf/meta/llama-3.3-70b-instruct-fp8-fast`,
which handles casual chat and slang, falling back to `@cf/meta/m2m100-1.2b`).
Every request must carry a Google Play Integrity token; the Worker asks Google to decode it and only
answers when the app is the genuine Google Play install (`PLAY_RECOGNIZED`, `LICENSED`) on a device
that meets device integrity, and the token was made for this exact text in the last 5 minutes.
Pirated or modified copies get `403 not_genuine`.

## API

`POST /translate` with header `X-Integrity-Token` and body
`{"text": "...", "source": "id", "target": "en", "style": "casual", "mode": "translate", "explain": true}`.
Languages: `id`, `en`, `fr`, `zh`, `ja`. Up to 2000 characters. Returns `{"text": "...", "meaning": "..."}`.

- `style`: `casual` (default), `polite` or `formal`, how the result should sound.
- `mode`: `translate` (default), or `polish` to fix the grammar of a text in its own language
  (then `source` and `target` must be the same), or `reply` to suggest replies to a message received in
  `source`, written in `target`. Reply mode returns `{"replies": [{"text": "...", "meaning": "..."}]}` with
  three replies, each with its Indonesian `meaning` unless `target` is `id`; `explain` must be `false`.
- `explain`: when `true`, `meaning` holds the result translated back into Indonesian (not when the target
  is Indonesian). It costs a second model call.

The integrity token's request hash is `sha256("v2|<source>|<target>|<style>|<mode>|<explain>|<text>")`.
App versions from before these options send only text, source and target and hash `"<source>|<target>|<text>"`;
the server still accepts that.

## One-time setup

1. **Cloudflare.** Create a free account at https://dash.cloudflare.com. Copy the *Account ID*
   (right side of the Workers & Pages page). Create an API token with the "Edit Cloudflare Workers"
   template, and also give it *Workers AI: Edit*.
2. **Google Cloud.** In Play Console, open *Test and release â†’ App integrity â†’ Play Integrity API*
   and link a Google Cloud project (create one if needed). In that project, enable the
   *Google Play Integrity API*, then create a service account (*IAM â†’ Service accounts*) and
   download a JSON key for it. Note the project **number** (not ID) from the project dashboard.
3. **GitHub repository secrets** (*Settings â†’ Secrets and variables â†’ Actions â†’ Secrets*):
   `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`, and `GOOGLE_SERVICE_ACCOUNT` (the whole JSON key).
4. Run the *Deploy translation server* workflow (or push a change under `server/`). The log shows the
   Worker URL, like `https://zidny-translate.<your-subdomain>.workers.dev`.
5. **GitHub repository variables** (*Variables* tab): `TRANSLATE_URL` = that Worker URL and
   `CLOUD_PROJECT_NUMBER` = the Google Cloud project number. Then rebuild the app.

## Testing

Debug APKs and sideloaded copies are not recognised by Google Play, so the server refuses them.
Test with a build installed from a Play testing track, or temporarily set `REQUIRE_INTEGRITY = "false"`
in `wrangler.toml` and redeploy (remember to switch it back).

`npm test` runs the unit tests with Node 20+.

Workers AI's free plan includes a daily allowance of requests; heavy use needs the Workers Paid plan.
