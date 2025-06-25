[![Clojars Project](https://img.shields.io/clojars/v/io.github.fourteatoo/clj-blink.svg?include_prereleases)](https://clojars.org/io.github.fourteatoo/clj-blink)
[![cljdoc badge](https://cljdoc.org/badge/io.github.fourteatoo/clj-blink)](https://cljdoc.org/d/io.github.fourteatoo/clj-blink)
[![CircleCI](https://dl.circleci.com/status-badge/img/gh/fourteatoo/clj-blink/tree/main.svg?style=shield)](https://dl.circleci.com/status-badge/redirect/gh/fourteatoo/clj-blink/tree/main)


# clj-blink

A Clojure library to use the Blink Camera REST API.  The protocol is
not officially disclosed but has been reverse engineered by Matt
Weinecke.  See https://github.com/MattTW/BlinkMonitorProtocol

## Usage

Require the library in your source code

```clojure
(require '[fourteatoo.clj-blink.api :as blink])
```

The first time you use the library you need to register your client
and obtain a unique id (returned in the resulting `BlinkClient`
object).

```clojure
(def client (blink/register-client "your@mail.address" "yourpassword"))
```

This will trigger whatever you have configured for 2FA.  A PIN will be
sent by Blink to your designated authentication means: an email, an
SMS, whatever.  Type it in.

`register-client` returns a `BlinkClient` object like the following:

```clojure
{:email "your@email.address",
 :password "yourpassword",
 :unique-id "some-pseudo-random-hexadecimal-string",
 :account-id 42,
 :client-id 1337,
 :auth-token #<Atom@73fdfb7f: "anauthenticationtoken">,
 :tier "yourregion"}
```

Any subsequent authentications (next time your app restarts) should
instead do:

```clojure
(def client (blink/authenticate-client "your@mail.address" "yourpassword" "unique-id"))
```

The `username` and `password` are the same as those used for the
`register-client`.  The `unique-id` is the same one returned (and
displayed) by `register-client`
("some-pseudo-random-hexadecimal-string" in the example above).

The authentication (the `auth-token`) is valid for 24 hours and is
automatically renewed by this libary.

You can use your client parameters like this:

```clojure
(blink/get-networks client)
```

which will return something like:

```clojure
{:summary {12345 {:name "Home", :onboarded true}},
 :networks
 [{:description "",
   :updated-at "2025-06-02T15:47:52+00:00",
   :autoarm-geo-enable false,
   :video-count 0,
   :dst true,
   :storage-total 0,
   :locale "",
   :lfr-channel 0,
   :name "Home",
   :video-history-count 4000,
   :arm-string nil,
   :ping-interval 60,
   :armed false,
   :busy nil,
   :account-id 42,
   :sync-module-error nil,
   :id 12345,
   :storage-used 0,
   :camera-error nil,
   :location-id nil,
   :lv-mode "relay",
   :feature-plan-id nil,
   :video-destination "server",
   :encryption-key nil,
   :autoarm-time-enable false,
   :network-origin "normal",
   :created-at "2015-12-13T21:13:19+00:00",
   :sm-backup-enabled false,
   :time-zone "Europe/Berlin",
   :network-key "abase64string"}]}
```

then you can, for instance, arm all your systems (assuming you have
more than one):

```clojure
(->> (get-networks client)
     :networks
     (run! #(system-arm client (:id %))))
```

or disarm them, substituting `system-arm` for `system-disarm`.


## Documentation

You can have a look at [![cljdoc](https://cljdoc.org/badge/io.github.fourteatoo/clj-blink)](https://cljdoc.org/d/io.github.fourteatoo/clj-blink)

or you can create your own local documentation with:

```shell
$ lein codox
```

and then read it with your favorite browser

```shell
$ firefox target/doc/index.html
```


## License

Copyright © 2025 Walter C. Pelissero

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
