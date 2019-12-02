package no.nav.eessi.pensjon.listeners


//@Configuration
//class OppgaveConfig {
//
//    @Value("\${spring.kafka.bootstrap-servers}")
//    lateinit var brokers: String
//
//    @Value("\${kafka.oppgave.groupid}")
//    lateinit var gruppeid: String
//
//    @Value("\${app.name}")
//    lateinit var appName: String
//
////    @Value("\${srveessipensjon.username}")
////    lateinit var username: String
////
////    @Value("\${srveessipensjon.password}")
////    lateinit var password: String
////
//
//    @Bean
//    fun oppgaveConsumerFactory(): ConsumerFactory<String, OppgaveMelding> {
//
//        val props = HashMap<String, Any>()
//        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers)
//        props.put(ConsumerConfig.GROUP_ID_CONFIG, gruppeid)
//        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
//        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
//
//        return DefaultKafkaConsumerFactory(
//                props,
//                StringDeserializer(),
//                JsonDeserializer(OppgaveMelding::class.java))
//    }
//
//    @Bean
//    fun oppgaveKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, OppgaveMelding> {
//
//        val factory = ConcurrentKafkaListenerContainerFactory<String, OppgaveMelding>()
//        //factory.setErrorHandler(kafkaErrorHandler)
//        factory.setConsumerFactory(oppgaveConsumerFactory())
//        return factory
//    }
//}