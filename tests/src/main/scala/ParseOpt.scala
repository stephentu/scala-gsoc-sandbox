package localhost.test

object ParseOpt {
  def containsOpt(args: Array[String], opt: String) = args.filter(_.startsWith(opt)).size > 0
  def parseOptList(args: Array[String], opt: String): List[String] = 
    parseOptString(args, opt).split(",").toList
  def parseOptListDefault(args: Array[String], opt: String, default: List[String]): List[String] = 
    if (containsOpt(args, opt))
      parseOptList(args, opt)
    else
      default
  def parseOptString(args: Array[String], opt: String) = args.filter(_.startsWith(opt)).head.substring(opt.size)
  def parseOptStringDefault(args: Array[String], opt: String, default: String) = 
    if (containsOpt(args, opt))
      parseOptString(args, opt)
    else
      default
  def parseOptInt(args: Array[String], opt: String) = parseOptString(args, opt).toInt
  def parseOptIntDefault(args: Array[String], opt: String, default: Int) =
    if (containsOpt(args, opt))
      parseOptInt(args, opt)
    else
      default
}
