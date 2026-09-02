package filters

import javax.inject._
import play.api.http.HttpFilters
import play.api.mvc.EssentialFilter

// Register your filters here, then add to application.conf:
//   play.http.filters = "filters.Filters"
@Singleton
class Filters @Inject()(correlationId: CorrelationIdFilter) extends HttpFilters {
  override val filters: Seq[EssentialFilter] = Seq(correlationId)
}
