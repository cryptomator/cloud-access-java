module org.cryptomator.cloudaccess {
	exports org.cryptomator.cloudaccess;
	exports org.cryptomator.cloudaccess.api;
	exports org.cryptomator.cloudaccess.api.exceptions;
	exports org.cryptomator.cloudaccess.requestdecorator;

	requires java.xml;
	requires com.google.common;
	requires org.cryptomator.cryptolib;
	requires org.slf4j;
	requires okhttp3;
	requires kotlin.stdlib;
	requires okhttp.digest;
	requires okio;
	requires com.auth0.jwt;
	requires com.github.benmanes.caffeine;
}