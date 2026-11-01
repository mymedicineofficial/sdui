# SduiTest

Android (Kotlin + Jetpack Compose) test project for Server-Driven UI.
GitHub repo = server. App `server/*.json` files ko raw.githubusercontent.com se fetch karke UI banata hai.

## Setup
1. Naya GitHub repo banao, ye poora project push karo (`server/` folder root me rahe).
2. `app/src/main/java/com/sdui/test/ServerConfig.kt` me GITHUB_USER aur GITHUB_REPO daalo.
   (Repo public hona chahiye, kyunki raw URL bina token ke chalta hai.)
3. Android Studio me open -> Sync -> Run.
4. `server/home.json` edit -> commit/push -> app me "Refresh" dabao. UI badal jayegi.

## JSON components
text, button, image, card, row, column, spacer
Common props: padding, margin, bg, radius, fillWidth, action
Actions: toast, open_url, open_screen, back

Note: GitHub raw ~5 min cache karta hai; app URL me ?t=timestamp lagata hai isliye mostly turant update dikhta hai.
