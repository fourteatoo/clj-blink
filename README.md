# clj-blink

A Clojure library to use the Blink Camera REST API.  The protocol is
not officially discoled but has been reverse engineered by Matt
Weinecke.  See https://github.com/MattTW/BlinkMonitorProtocol

## Usage

Require the library in your source code

```clojure
(require '[fourteatoo.clj-blink.api :as blink])
```

The first time you use the library you need to register your client
and obtain an access token

```clojure
(def client (blink/register-client "username" "password"))
```

This will trigger whatever you have configured for 2FA; a PIN will be
sent to you by Blink to your designated authentication means: an
email, a SMS, whatever.

Save the returned client map for later use.  The client map
contains sensitive info, so don't disclose it.

You can use your client parameters like this:

```clojure
(blink/get-networks client)
```

then you can, for instance, arm all your systems (assuming you have
more than one):

```clojure
(->> (get-networks client)
     :networks
     (run! #(system-arm client (:id %))))
```

or disarm them, substituting `system-arm` for `system-disarm`.

Have a look at https://github.com/MattTW/BlinkMonitorProtocol and the
source code.


## Reference

An API documentation can be generated with

```shell
$ lein codox
```

the you can read it with your favorite browser

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
