val a = remoteActor(9100, 'echo) {
  loop {
    react {
      case e => 
        println("received: " + e)
        sender ! e
    }
  }
}
