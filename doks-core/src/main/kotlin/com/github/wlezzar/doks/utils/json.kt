package com.github.wlezzar.doks.utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue

val json = ObjectMapper().findAndRegisterModules()
val yaml = ObjectMapper(YAMLFactory()).findAndRegisterModules()

fun jsonObject(action: ObjectNode.() -> Unit): ObjectNode = json.createObjectNode().apply(action)
fun jsonArray(action: ArrayNode.() -> Unit): ArrayNode = json.createArrayNode().apply(action)

inline fun <reified T> String.parseJson(): T = json.readValue(this)
fun Any.toJsonNode(): JsonNode = json.valueToTree(this)

inline fun <reified T> JsonNode.toValue(): T = json.treeToValue(this, T::class.java)