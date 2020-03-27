# YaCy Grid Component: Core

## What is this?
The YaCy Grid Core is a Leightweight Microservice Operating Environment.
It includes:
* a jetty-based web server
* a very lightweight JSON library using the JSON-Java reference implementation
* an infrastructure to implement servlets using these JSON classes
* libraries for network communication and http clients
* file-based storage classes for JSON objects
* image-generating classes
* a framework for time-series data including image graph generation

This combination of a lightweight web server together with JSON, time series
and graphics was arranged because most of API-implementing servers need the
ability to easily implement a JSON API, handle network communication,
be able to statify without the need of a database connection and also to
support a telemetry service which makes it possible to monitor a large number
of instances within a scalable microservice environment in an easy way.

## How to use this library?
Right now we do not intend to publish the library as a jar file, instead we
recommend to add this repository as a git submodule.
If you have an existing project, do the following to add yacy_grid_core as
as submodule:
```
# open a shell and navigate to your project root path
mkdir submodules
cd submodules
git submodule add https://github.com/yacy_yacy_grid_core
```

Your project `<your_project>` then can be checked out with
```
git clone --recursive https://github.com/<your_project>.git
```

and an update to an already checked out project can be made with
```
git pull origin master
git submodule foreach git pull origin master
```

## How to compile and run yacy_grid_core
Because this is a libaray and not an application there is not really a use
running the program at all. However for demonstration purpose you can actually
start the server without any usefull service.

To build and run, do:
```
gradle assemble
gradle run
```


## What is the software license?
LGPL 2.1 (C) by Michael Peter Christen

Have fun!
@0rb1t3r
