# co-working

Co-working is a Pallet library designed to launch a server on a cloud provider of your choosing.

The server can be optionally configured for shared development in multiple language environments.


## Installation && Quickstart

Install [leingingen][leingingen].  Currently leiningen 1 is required.

###Clone the library
```bash
git clone git@github.com:PHLClojure/co-working.git
```
```bash
cd co-working
```

###Get dependencies with leiningen and enter the REPL
```bash
lein deps
```
```bash
lein repl
```

###Prepare the REPL
```clojure
(use 'co-working.core) (in-ns 'co-working.core)
```

## Usage

###Launch a node
```clojure
(def cap (core/converge {co-worker-cs 1} :compute aws-srvc))
```

###Destroy all running nodes
```clojure
(def cap (core/converge {co-worker-cs 0} :compute aws-srvc))
```

## License

Copyright © 2012 PHLCLJ, Hunter Hutchinson

Distributed under the Eclipse Public License, the same as Clojure.
