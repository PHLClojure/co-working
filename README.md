# co-working

Co-working is a Pallet library designed to launch a server on a cloud provider of your choosing.

The server can be optionally configured for shared development in multiple language environments.


## Installation && Quickstart

Install https://github.com/technomancy/leiningen.  

Currently leiningen 1 is required.

###Clone the library
```bash
git clone git@github.com:PHLClojure/co-working.git
```
```bash
cd co-working
```

###Get dependencies with leiningen
```bash
lein deps
```

###Configure provider access credentials.

```bash
lein pallet add-service aws aws-ec2 "your-aws-key" "your-aws-secret-key"
```

Note that this creates a ~/.pallet/services/aws.clj file with your credentials in it.

The second argument above is the name of the jclouds provider, which is cloud specific. To find the value for other clouds, you can list the supported providers with:

```bash
lein pallet providers
```

###Prepare the REPL
```bash
lein repl
```

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
