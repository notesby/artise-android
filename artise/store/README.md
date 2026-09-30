# Publishing Artise on Google Play

Everything needed for the Play Console, ready to copy. The listing texts and banners are in `listing/`
(`en-US` and `es-419`, which is Latin American Spanish).

## 1. The upload key (keep it safe)

- The key is in `~/.artise-keys/` on the build computer. `artise-upload.jks` is the key. `upload.properties` holds its
  passwords. Both are readable only by you and are never committed.
- **Back up both files now** in a password manager or on an encrypted drive. With Play App Signing, Google keeps the
  real signing key, so a lost upload key can be reset through Play support. The reset takes days, and no update can
  ship until it's done.
- When the key is on the computer, release builds are signed with it automatically. Elsewhere, set
  `ARTISE_UPLOAD_PROPERTIES` to the properties file's path. Without it, release builds fall back to the debug key and
  print a warning. Play refuses those builds.

## 2. Building the bundle

Run it alone. Nothing else should use Gradle on this machine while it builds.

```
./gradlew --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process \
  "-Dorg.gradle.jvmargs=-Xmx9g -Dfile.encoding=UTF-8 -XX:+UseG1GC" :app:bundleGplayRelease
```

The bundle is `app/build/outputs/bundle/gplayRelease/app-gplay-release.aab`.

- **Version:** the version code comes from `plugins/src/main/kotlin/Versions.kt` (year, month, release number).
- **Uploads:** every upload needs a higher version code. Bump `versionReleaseNumber` before each one.

## 3. Play Console, in order

1. **Create the app:** name Artise, default language Spanish (Latin America) or English, App, Free.
2. **Choose App Signing:** accept Play App Signing, managed by Google.
3. **Internal testing:**
   - Create a release, upload the `.aab` and add release notes.
   - Add testers by email. Up to 100 people can install it from Play within minutes, with no review wait.
4. **Store listing:**
   - **Texts:** paste them from `listing/<language>/`.
   - **Icon:** `artise/appicon/src/main/ic_launcher-playstore.png` (512×512).
   - **Feature graphic:** `listing/<language>/feature-graphic.png` (1024×500).
   - **Screenshots:** take 2 to 8 on your phone, with power + volume down. Use chats and notes without private family
     content.
5. **App content:** fill in the forms with the answers below.
6. **Production:**
   - **Personal account created after November 2023:** Google first requires a closed test with at least 12 testers
     who stay opted in for 14 days in a row.
   - **Organization account:** no such wait.

## 4. App content answers

### Privacy policy

`https://artise.co/privacy.html`. Before submitting, the page needs a section about the phone app
(see section 6: request for the server agent).

### App access

Choose **"All or some functionality is restricted"** and give Google a reviewer account. Ask the server agent to
create one with a sample chat that includes Ari and a few notes. Instructions for the reviewer:

> Open the app and tap "Sign in". The server (artise.co) is already set. Username: `<reviewer account>`,
> password: `<password>`. If asked to set up recovery, use the recovery key: `<key>`. The "Demo" chat includes
> Ari, the assistant. Ask it something, e.g. "remind me in 5 minutes to check the oven". Notes are under the chat's
> details, "Notes".

### Ads

No ads.

### Content rating

Use the questionnaire's "Communication" or "Social" category.

- **Users can talk to each other or share content:** yes.
- **Shares the user's location with other users:** yes.
- **Buying digital goods:** no.
- **Violence, sexual content, gambling or drugs:** none.

### Target audience

**13 and over** (or 18+). Choosing any age under 13 puts the app under the Families policy, which has stricter rules.

### Data safety

- **Encryption and deletion:**
  - Data is encrypted in transit: **yes**.
  - People can ask for their data to be deleted: **yes**. They can deactivate their account in the app, or use the
    privacy request form on artise.co.
- **Shared with third parties: none.** The AI providers and hosting companies listed in the privacy notice are
  service providers acting for Artise. Google doesn't count those as "sharing".
- **Collected:** all of the following. Purpose: app functionality. Required, because it's how the app works, except
  location and voice, which people choose to send.

| Data type | Why |
|---|---|
| Name, email address, user IDs | The account, invitations and profile. Purpose: app functionality, account management. |
| Messages (other in-app messages) | Chats. They're end-to-end encrypted, but Ari (run by Artise) reads the chats it's in. |
| Photos, videos | Sent in chats and added to notes. |
| Voice or sound recordings | Voice messages (optional). |
| Files and documents | Sent in chats and added to notes. |
| Other user-generated content | Notes. |
| Approximate and precise location | Only when someone shares their location in a chat (optional). |
| Device or other IDs | The push token that routes notifications to this phone. |

**Not collected:** contacts, calendar, health, financial info, web history, app activity analytics, crash logs,
diagnostics. The app has no analytics or crash reporting.

### Permission declarations

**Foreground service: location.**

> Live location sharing: when the user chooses to share their live location in a chat (for example while
> driving home), a foreground service keeps sending their position to that chat until they stop sharing or the
> time they chose runs out. An ongoing notification shows while sharing, with a button to stop. Nothing is sent
> without the user starting it.

Google asks for a short video (under 30 s): open a chat, tap the attachment button, choose Location, choose "Share
live location", show the notification, then stop it.

**Foreground service: microphone.**

> Voice and video calls: keeps the call's audio going when the user leaves the app during a call. It runs only
> during a call the user started or accepted, with an ongoing call notification.

**Full-screen intent.** Category: "Calling".

> Shows the incoming call screen when someone calls the user in a chat, including when the phone is locked.

**Ignore battery optimizations** (if Play asks):

> Artise is a messaging and calling app. Some phones (Xiaomi, Samsung and others) stop apps in the background and
> delay or drop notifications for messages, calls and reminders. The app shows a banner offering the exemption only
> after a notification failed to arrive, and the user can dismiss it or say no.

## 5. What Artise changes from Element for Play

- **Install permission:** `REQUEST_INSTALL_PACKAGES` is removed from Play builds (`app/src/gplay/AndroidManifest.xml`). Play only allows it for apps
  whose main purpose is installing apps. An APK sent in a chat now opens in the system installer, which asks by
  itself.
- **Analytics and crash reports:** none are built in. The PostHog and Sentry keys are empty in
  `plugins/src/main/kotlin/config/BuildTimeConfig.kt`.

## 6. Request for the server agent: the privacy notice and a reviewer account

Add a section about the phone app to `https://artise.co/privacy.html`, in both languages. Suggested English text:

> **The Artise app for Android.** The app connects only to your organization's Artise server, with these
> exceptions. To deliver notifications, your server sends Google's Firebase Cloud Messaging a message with only an
> event ID (never the message itself). The app then fetches the message from your server and decrypts it on the
> phone. Google receives a push token for your phone to route those notifications. The app uses your location only
> when you share it in a chat, once or live, and live sharing shows a notification until you stop it. The camera
> and microphone are used only for photos, voice messages and calls you start. The app has no ads, analytics or
> crash reporting.

Add Google LLC to the processors table: "Delivering app notifications (Firebase Cloud Messaging), only an event ID;
United States".

Also create a reviewer account for Google Play review, with:

- a chat named "Demo" that includes Ari and has a few notes;
- a password you can hand to Google;
- recovery set up, with the recovery key saved.

It must not be part of any family chat.
