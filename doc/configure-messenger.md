## Facebook Messenger Setup

- On Facebook, create a Page for your service.

### Configuring Messenger

From your [Facebook developer account](https://developers.facebook.com):

- Create a new facebook app from the My Apps menu.

From the displayed configuration page for the app:

#### Under *Products*

- add product: **Messenger**.

#### Under *Token Generation*

- Select the facebook page you created earlier.
- Add the resulting new access token as an environment var for the server by executing in the Terminal:

    heroku config:set FACEBOOK_ACCESS_TOKEN=xxxxxx

#### Under *Webhooks*

- First make sure your app is installed on heroku and up running, as the webhook on the server will be accessed by facebook to verify it.
- Configure the facebook webhook *Callback Url* to point to `https://appname.herokuapp.com/fbme/webhook` where `appname` is the name of your app on heroku.
- Set the facebook verify token to a complex string of your choice.
- Set the facebook verify token var on the server to a string of your choice by executing in the terminal:

    heroku config:set FACEBOOK_VERIFY_TOKEN="some secret text"

- edit the subscription fields to enable "messages" only.
- Verify and save the Webhooks dialog.
- If the webhook fails to be accepted by facebook, troubleshoot as needed.
- Select your page as subscriber to webhook events.

#### Under *Built-in NLP*

- Enable *Built-in NLP* which provides language analysis from [wit.ai](https://wit.ai)

#### Under *App Review*

- Select `pages_messaging` and `pages_messaging_subscriptions` then submit for review (optional for development but required to make the service public).
- Note: You may have to update the facebook page as requested.

### Configuring the Messenger Webhook

- Select *WebHooks* on the drawer or if not listed, add a [webhook](https://developers.facebook.com/docs/messenger-platform/webhook-reference) "product" to handle messages for the app.
- Set the type of the webhook to `page` if needed.

### Set Local Environment

Review https://devcenter.heroku.com/articles/heroku-local#set-up-your-local-environment-variables

    touch .env
    heroku config:get FACEBOOK_ACCESS_TOKEN -s  >> .env
    heroku config:get FACEBOOK_VERIFY_TOKEN -s  >> .env

## Configuring the Chat Extension

The Facebook chat extension provides a menu and view within Messenger. You can set the profile from the terminal or alternatively in a REPL.

### Option A: Set Profile from Terminal

You can use use the [Messenger Profile API](https://developers.facebook.com/docs/messenger-platform/reference/messenger-profile-api) to set the properties from the command line. Use the access token from earlier.  

- Whitelist the domain of the server

Optionally:

- Create a messenger extension link in the facebook messenger drawer
- Enable a Getting Started button in Messenger
- Set up a menu in Messenger

### Alternative B: Set Profile From REPL

For convenience you can set the Messenger Profile from the REPL connected to the server.

- Evaluate to provide configuration commands:

    (in-ns 'api.facebook.messenger)

- Whitelist the domain of the server:

    (send-whitelist-domains ["https://appname.herokuapp.com/"])

- To create a messenger extension link in the facebook messenger drawer, using the name of your own app, evaluate:

    (send-home-url {:url "https://appname.herokuapp.com/"
                    :webview_height_ratio "tall"
                    :in_test "true"})

- Enable a Getting Started button in Messenger:

    (send-get-started {:payload "START"})

- Set up a menu in Messenger:

    (send-persistent-menu
         [{:locale "default"
           :call_to_actions
           [(url-button "Open View"
                        :messenger-extensions true
                        :url "https://appname.herokuapp.com"
                        :webview-height-ratio "tall")]}])
