# MEKit

Toolkit for rapid iterative development of Messenger Extensions apps using Clojurescript and/or Javascript.

Runs on Heroku with hotloaded changes and isomorphic/universal codebase.

Fork on github as a starting point for your own projects.
Soon to be available as a Leiningen template.

## Features

- SDK for messenger extensions
- SDK for Messenger
- Schema (spec in clojure parlance) for messenger datastructures

## Requirements

leiningen, heroku, npm

All Terminal commands should be executed in the root directory of the project, unless otherwise noted.

## Run Locally

To start a server on your own computer:

    lein do clean, deps, compile
    lein run

Point your browser to the displayed local port.
Click on the displayed text to refresh.

If used with heroku, alternatively start locally with :

    heroku local web

## Deploy to Heroku

To start a server on Heroku:

    heroku apps:create
    git push heroku master

Open the site in a browser to verify that the page is served:

    heroku open

Consider enabling SSL for the Heroku app (requires a paid Heroku account).

## Configure Messenger

See `doc/configure-messenger.md` for instructions about configuring Messenger to connect with the runtime. Note the required environment variables for Heroku.

## Setting up a Tunnel

Requirements: [ngrok](https://ngrok.com/) or similar secure tunnel to localhost.

The `ngrok.yml` file in the project directory configures tunnels for the server as well as the live update connection.

Start ngrok in a separate Terminal after changing into the project directory:

    ngrok start -config=./ngrok.yml --all

Note the urls provided by ngrok for port 3449 and 5000.

In the project.clj file edit the websocket-host to the hostname (url without http://) from the url provided by ngrok for port 3449. Same for websocket-url. Then commit and push to heroku.

Redirect heroku server events to the local development server by executing the following, substituting the url with the one exposed by ngrok for port 5000:

    heroku config:add REDIRECT=https://1f19027a.ngrok.io

If you have purchased an authentication key for ngrok, optionally edit ngrok.yml with hostnames to avoid having to redo these steps before interactive development sessions.

## Development Workflow

Start figwheel for interactive development with
automatic builds and code loading:

    lein figwheel server app

Figwheel will push code changes to the app and local server.
Wait until Figwheel is ready to connect, then
start a server in another terminal:

    heroku local web

Open the displayed URL in a browser to verify that it works.

The facebook chat extensions webview should now be provided by your local server,
with live updates when you change files.

When done developing, disable the server redirect:

    heroku config:remove REDIRECT

## Testing

To run a test of the system, execute:

    lein test

## License

Copyright © 2017 Terje Norderhaug

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
