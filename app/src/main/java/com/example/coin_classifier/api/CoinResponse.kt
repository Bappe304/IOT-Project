package com.example.coin_classifier.api

data class CoinResponse (
    val class_name: String,
    val confidence: Float,
    val all_probabilities: Map<String, Float>
)