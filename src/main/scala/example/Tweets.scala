package example

/**
  * Created by sai on 2016/10/3.
  */
object Tweets {

  final case class Author(handle: String)
  final case class Hashtag(name: String)

  final case class Tweet(author: Author, timestamp: Long, body: String) {
    def hashtags: Set[Hashtag] =
      body.split(" ").collect {
        case t if t.startsWith("#") => Hashtag(t)
      }.toSet
  }
}
