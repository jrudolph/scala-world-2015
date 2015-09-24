# Sources for the akka-stream / akka-http scala.world 2015 conference talk

To test, run this in sbt:

```
log-service/re-start
backend/runMain example.repoanalyzer.Step6
```

and the open the browser at http://localhost:8080. All the other steps should work as well.

Geo IP resolution uses the server at http://freegeoip.net/ which only allows a certain quota of free requests per time
(though requests will be cached between runs, see `Cache` and the `ip-cache` folder). Also, we experienced some downtime
of this free server so we included part of the cache.

The sources contain an example log file to replay which contains a big amount of nonsense data in the right format.