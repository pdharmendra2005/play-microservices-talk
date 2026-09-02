package filters

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import org.apache.pekko.stream.Materializer
import play.api.mvc._

// GUIDE: Part 3, "Correlation IDs"
// Reads X-Request-Id from the incoming request (or generates one), puts it in
// MDC/attrs so every log line carries it, and echoes it on the response.
// Your clients then forward the same value downstream.
@Singleton
class CorrelationIdFilter @Inject()(implicit val mat: Materializer, ec: ExecutionContext)
  extends Filter {

  // TODO: def apply(next: RequestHeader => Future[Result])(rh: RequestHeader): Future[Result] = ...

  def apply(next: RequestHeader => Future[Result])(rh: RequestHeader): Future[Result] = next(rh)
}
