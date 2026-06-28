import 'dart:io';
import 'package:dio/dio.dart';

/// Custom exception classes to represent different API errors.
abstract class ApiException implements Exception {
  final String message;
  final int? statusCode;

  ApiException(this.message, [this.statusCode]);

  @override
  String toString() => '$runtimeType: $message (Status: $statusCode)';
}

class BadRequestException extends ApiException {
  BadRequestException(String message, [int? statusCode]) : super(message, statusCode);
}

class UnauthorizedException extends ApiException {
  UnauthorizedException(String message, [int? statusCode]) : super(message, statusCode);
}

class ForbiddenException extends ApiException {
  ForbiddenException(String message, [int? statusCode]) : super(message, statusCode);
}

class NotFoundException extends ApiException {
  NotFoundException(String message, [int? statusCode]) : super(message, statusCode);
}

class ServerException extends ApiException {
  ServerException(String message, [int? statusCode]) : super(message, statusCode);
}

class NetworkException extends ApiException {
  NetworkException(String message) : super(message);
}

class UnknownException extends ApiException {
  UnknownException(String message, [int? statusCode]) : super(message, statusCode);
}

/// Token model representing the JWT access and refresh tokens.
class TokenModel {
  final String accessToken;
  final String refreshToken;

  TokenModel({
    required this.accessToken,
    required this.refreshToken,
  });

  factory TokenModel.fromJson(Map<String, dynamic> json) {
    return TokenModel(
      accessToken: json['access_token'] as String,
      refreshToken: json['refresh_token'] as String,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'access_token': accessToken,
      'refresh_token': refreshToken,
    };
  }
}

/// Abstract storage interface for storing and retrieving JWT tokens.
/// In a real Flutter app, implement this using flutter_secure_storage.
abstract class TokenStorage {
  Future<void> saveTokens(TokenModel tokens);
  Future<TokenModel?> getTokens();
  Future<void> clearTokens();
}

/// Simple in-memory token storage implementation as a fallback/example.
class InMemoryTokenStorage implements TokenStorage {
  TokenModel? _tokens;

  @override
  Future<void> saveTokens(TokenModel tokens) async {
    _tokens = tokens;
  }

  @override
  Future<TokenModel?> getTokens() async {
    return _tokens;
  }

  @override
  Future<void> clearTokens() async {
    _tokens = null;
  }
}

/// A highly modular and robust API Client built with Dio.
/// Handles JWT authentication, request interceptors for token refreshing,
/// and standardized FastAPI error handling.
class ApiClient {
  late final Dio _dio;
  final TokenStorage tokenStorage;
  final String baseUrl;

  ApiClient({
    required this.baseUrl,
    required this.tokenStorage,
    Dio? dio,
  }) {
    _dio = dio ?? Dio();
    _setupDio();
  }

  Dio get dio => _dio;

  void _setupDio() {
    _dio.options = BaseOptions(
      baseUrl: baseUrl,
      connectTimeout: const Duration(seconds: 15),
      receiveTimeout: const Duration(seconds: 15),
      sendTimeout: const Duration(seconds: 15),
      contentType: Headers.jsonContentType,
      responseType: ResponseType.json,
    );

    _dio.interceptors.addAll([
      // 1. Auth Interceptor to attach JWT and handle auto-refreshing
      AuthInterceptor(
        dio: _dio,
        tokenStorage: tokenStorage,
        baseUrl: baseUrl,
      ),
      // 2. Logging Interceptor (useful for debugging API requests)
      LogInterceptor(
        requestHeader: true,
        requestBody: true,
        responseHeader: false,
        responseBody: true,
        error: true,
      ),
    ]);
  }

  /// Standard GET Request
  Future<Response<T>> get<T>(
    String path, {
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
  }) async {
    try {
      return await _dio.get<T>(
        path,
        queryParameters: queryParameters,
        options: options,
        cancelToken: cancelToken,
      );
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Standard POST Request
  Future<Response<T>> post<T>(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
  }) async {
    try {
      return await _dio.post<T>(
        path,
        data: data,
        queryParameters: queryParameters,
        options: options,
        cancelToken: cancelToken,
      );
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Standard PUT Request
  Future<Response<T>> put<T>(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
  }) async {
    try {
      return await _dio.put<T>(
        path,
        data: data,
        queryParameters: queryParameters,
        options: options,
        cancelToken: cancelToken,
      );
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Standard PATCH Request
  Future<Response<T>> patch<T>(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
  }) async {
    try {
      return await _dio.patch<T>(
        path,
        data: data,
        queryParameters: queryParameters,
        options: options,
        cancelToken: cancelToken,
      );
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Standard DELETE Request
  Future<Response<T>> delete<T>(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
  }) async {
    try {
      return await _dio.delete<T>(
        path,
        data: data,
        queryParameters: queryParameters,
        options: options,
        cancelToken: cancelToken,
      );
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Translates DioException into friendly and descriptive backend Exceptions.
  /// Formatted to parse standard FastAPI JSON error structures (e.g., `{"detail": "..."}`)
  ApiException _handleDioError(DioException error) {
    if (error.type == DioExceptionType.connectionTimeout ||
        error.type == DioExceptionType.sendTimeout ||
        error.type == DioExceptionType.receiveTimeout ||
        error.error is SocketException) {
      return NetworkException('Network connection timeout. Please check your internet connection.');
    }

    final response = error.response;
    if (response == null) {
      return UnknownException('An unexpected error occurred. No server response was received.');
    }

    final int statusCode = response.statusCode ?? 500;
    String errorMessage = 'Something went wrong';

    // Parse FastAPI standard error response `{"detail": "Error Message"}`
    // or `{"detail": [{"loc": [...], "msg": "...", "type": "..."}]}`
    if (response.data is Map<String, dynamic>) {
      final detail = response.data['detail'];
      if (detail is String) {
        errorMessage = detail;
      } else if (detail is List && detail.isNotEmpty) {
        final firstDetail = detail.first;
        if (firstDetail is Map<String, dynamic> && firstDetail.containsKey('msg')) {
          errorMessage = firstDetail['msg'] as String;
        }
      }
    } else if (response.data is String) {
      errorMessage = response.data as String;
    }

    switch (statusCode) {
      case 400:
        return BadRequestException(errorMessage, statusCode);
      case 401:
        return UnauthorizedException(errorMessage, statusCode);
      case 403:
        return ForbiddenException(errorMessage, statusCode);
      case 404:
        return NotFoundException(errorMessage, statusCode);
      case 500:
      case 502:
      case 503:
      case 504:
        return ServerException('Server error occurred ($statusCode). Please try again later.', statusCode);
      default:
        return UnknownException(errorMessage, statusCode);
    }
  }
}

/// QueuedInterceptor handles high-concurrency request scenarios correctly.
/// If multiple requests get a 401, they are queued. The first request triggers the refresh flow,
/// and subsequent requests wait for the refresh to finish and then retry with the new token.
class AuthInterceptor extends QueuedInterceptor {
  final Dio dio;
  final TokenStorage tokenStorage;
  final String baseUrl;
  bool _isRefreshing = false;

  AuthInterceptor({
    required this.dio,
    required this.tokenStorage,
    required this.baseUrl,
  });

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) async {
    // 1. Skip adding authorization token if request is for login or token-refresh
    if (options.path.contains('/auth/login') || options.path.contains('/auth/refresh')) {
      return handler.next(options);
    }

    // 2. Fetch locally persisted tokens
    final tokens = await tokenStorage.getTokens();
    if (tokens != null && tokens.accessToken.isNotEmpty) {
      options.headers['Authorization'] = 'Bearer ${tokens.accessToken}';
    }

    return handler.next(options);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) async {
    // Check if the server returned 401 Unauthorized
    if (err.response?.statusCode == 401) {
      final requestOptions = err.requestOptions;

      // Avoid infinite loops if refreshing itself fails with 401
      if (requestOptions.path.contains('/auth/refresh')) {
        await tokenStorage.clearTokens();
        return handler.next(err);
      }

      // Check if another parallel request is already refreshing the token
      if (_isRefreshing) {
        // Wait and then retry with the newly refreshed token
        try {
          final response = await _retryRequest(requestOptions);
          return handler.resolve(response);
        } on DioException catch (retryErr) {
          return handler.next(retryErr);
        }
      }

      _isRefreshing = true;

      try {
        final tokens = await tokenStorage.getTokens();
        final refreshToken = tokens?.refreshToken;

        if (refreshToken == null || refreshToken.isEmpty) {
          throw DioException(
            requestOptions: requestOptions,
            error: 'No refresh token available.',
          );
        }

        // Call your FastAPI backend refresh endpoint.
        // In FastAPI, this often accepts JSON like `{"refresh_token": "..."}` or a Bearer token.
        // We configure a separate isolated Dio instance for refreshing to prevent interceptor loops.
        final refreshDio = Dio(BaseOptions(baseUrl: baseUrl));
        final response = await refreshDio.post(
          '/auth/refresh',
          data: {'refresh_token': refreshToken},
        );

        if (response.statusCode == 200 && response.data != null) {
          // Parse and save new tokens
          final newTokens = TokenModel.fromJson(response.data as Map<String, dynamic>);
          await tokenStorage.saveTokens(newTokens);
          _isRefreshing = false;

          // Retry the original request
          final retriedResponse = await _retryRequest(requestOptions);
          return handler.resolve(retriedResponse);
        } else {
          // Refresh failed
          _isRefreshing = false;
          await tokenStorage.clearTokens();
          return handler.next(err);
        }
      } catch (e) {
        _isRefreshing = false;
        await tokenStorage.clearTokens();
        // Return original error or a custom refresh failure error
        return handler.next(err);
      }
    }

    return handler.next(err);
  }

  /// Retries a given request options by adding the newly refreshed token.
  Future<Response<dynamic>> _retryRequest(RequestOptions requestOptions) async {
    final tokens = await tokenStorage.getTokens();
    final newAccessToken = tokens?.accessToken;

    final options = Options(
      method: requestOptions.method,
      headers: {
        ...requestOptions.headers,
        if (newAccessToken != null) 'Authorization': 'Bearer $newAccessToken',
      },
    );

    return dio.request<dynamic>(
      requestOptions.path,
      data: requestOptions.data,
      queryParameters: requestOptions.queryParameters,
      options: options,
    );
  }
}
