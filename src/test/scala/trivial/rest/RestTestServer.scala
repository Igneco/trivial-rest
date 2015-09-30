package trivial.rest

trait RestTestServer {
  def httpGet(path: String, expectedStatus: Int = 200, expectedHeaders: Map[String,String] = Map.empty, expectedBody: String = "")
  def httpPost(path: String, postBody: String, expectedStatus: Int = 200, expectedHeaders: Map[String,String] = Map.empty, expectedBody: String = "")
  def httpPut(path: String, putBody: String, expectedStatus: Int = 200, expectedHeaders: Map[String,String] = Map.empty, expectedBody: String = "")
  def httpDelete(path: String, expectedStatus: Int = 200, expectedHeaders: Map[String,String] = Map.empty, expectedBody: String = "")
}