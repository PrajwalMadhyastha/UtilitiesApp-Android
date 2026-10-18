fun main() {
    val searchBody = """
    [
      {
        "link_src": "https://www.moneycontrol.com/mutual-funds/nav/hdfc-small-cap-fund/MMS002",
        "name": "HDFC Small Cap Fund Growth"
      },
      {
        "link_src": "https://www.moneycontrol.com/mutual-funds/nav/hdfc-small-cap-fund-direct-plan/MMS025",
        "name": "HDFC Small Cap Fund-Direct Growth Option"
      }
    ]
    """
    
    val fundName = "HDFC Small Cap Fund - Direct Plan"
    val isDirectFund = fundName.contains("direct", ignoreCase = true)
    val isRegularFund = fundName.contains("regular", ignoreCase = true)
    
    val blocks = searchBody.split("{")
    var bestUrlStr: String? = null
    var firstUrlStr: String? = null

    for (block in blocks) {
        val linkMatch = Regex(""""link_src"\s*:\s*"([^"]+)"""").find(block)
        val nameMatch = Regex(""""name"\s*:\s*"([^"]+)"""").find(block)
        
        if (linkMatch != null && nameMatch != null) {
            val url = linkMatch.groupValues[1]
            val name = nameMatch.groupValues[1]
            if (firstUrlStr == null) firstUrlStr = url
            
            if (isDirectFund && name.contains("direct", ignoreCase = true)) {
                bestUrlStr = url
                break
            }
            if (isRegularFund && name.contains("regular", ignoreCase = true)) {
                bestUrlStr = url
                break
            }
        }
    }
    
    val detailUrlStr = bestUrlStr ?: firstUrlStr
    println("Selected URL: " + detailUrlStr)
}
