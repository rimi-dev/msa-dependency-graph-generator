import type { GraphData, Project, SourceDetail } from '@/types';

export const MOCK_GRAPH_DATA: GraphData = {
  nodes: [
    {
      id: 'api-gateway',
      displayName: 'API Gateway',
      language: 'nodejs',
      framework: 'Express.js',
      dependencyCount: 5,
    },
    {
      id: 'auth-service',
      displayName: '인증서버',
      language: 'kotlin',
      framework: 'Spring Boot',
      dependencyCount: 6,
    },
    {
      id: 'user-service',
      displayName: '사용자서버',
      language: 'java',
      framework: 'Spring Boot',
      dependencyCount: 6,
    },
    {
      id: 'order-service',
      displayName: '주문서버',
      language: 'kotlin',
      framework: 'Spring WebFlux',
      dependencyCount: 6,
    },
    {
      id: 'payment-service',
      displayName: '결제서버',
      language: 'java',
      framework: 'Spring Boot',
      dependencyCount: 4,
    },
    {
      id: 'notification-service',
      displayName: '알림서버',
      language: 'typescript',
      framework: 'NestJS',
      dependencyCount: 4,
    },
    {
      id: 'inventory-service',
      displayName: '재고서버',
      language: 'python',
      framework: 'FastAPI',
      dependencyCount: 3,
    },
    {
      id: 'analytics-service',
      displayName: '분석서버',
      language: 'python',
      framework: 'Django',
      dependencyCount: 3,
    },
    {
      id: 'file-service',
      displayName: '파일서버',
      language: 'go',
      framework: 'Gin',
      dependencyCount: 2,
    },
    {
      id: 'search-service',
      displayName: '검색서버',
      language: 'java',
      framework: 'Spring Boot',
      dependencyCount: 3,
    },
    {
      id: 'config-service',
      displayName: '설정서버',
      language: 'kotlin',
      framework: 'Spring Cloud Config',
      dependencyCount: 5,
    },
    {
      id: 'frontend-bff',
      displayName: '프론트엔드 BFF',
      language: 'typescript',
      framework: 'NestJS',
      dependencyCount: 5,
    },
  ],
  edges: [
    {
      id: 'e1',
      source: 'api-gateway',
      target: 'auth-service',
      protocol: 'HTTP',
      method: 'POST',
      endpoint: '/auth/validate',
      confidence: 0.95,
      detectedBy: 'static-analysis',
      sourceLocationCount: 3,
    },
    {
      id: 'e2',
      source: 'api-gateway',
      target: 'user-service',
      protocol: 'HTTP',
      method: 'GET',
      endpoint: '/users/{id}',
      confidence: 0.9,
      detectedBy: 'static-analysis',
      sourceLocationCount: 5,
    },
    {
      id: 'e3',
      source: 'api-gateway',
      target: 'order-service',
      protocol: 'HTTP',
      method: 'POST',
      endpoint: '/orders',
      confidence: 0.92,
      detectedBy: 'static-analysis',
      sourceLocationCount: 4,
    },
    {
      id: 'e4',
      source: 'api-gateway',
      target: 'frontend-bff',
      protocol: 'HTTP',
      method: 'GET',
      endpoint: '/bff/*',
      confidence: 0.88,
      detectedBy: 'config-analysis',
      sourceLocationCount: 2,
    },
    {
      id: 'e5',
      source: 'order-service',
      target: 'payment-service',
      protocol: 'HTTP',
      method: 'ProcessPayment',
      endpoint: 'PaymentService/ProcessPayment',
      confidence: 0.97,
      detectedBy: 'proto-analysis',
      sourceLocationCount: 6,
    },
    {
      id: 'e6',
      source: 'order-service',
      target: 'inventory-service',
      protocol: 'HTTP',
      method: 'PUT',
      endpoint: '/inventory/reserve',
      confidence: 0.89,
      detectedBy: 'static-analysis',
      sourceLocationCount: 2,
    },
    {
      id: 'e7',
      source: 'order-service',
      target: 'notification-service',
      protocol: 'HTTP',
      endpoint: 'order.created',
      confidence: 0.93,
      detectedBy: 'mq-analysis',
      sourceLocationCount: 4,
    },
    {
      id: 'e8',
      source: 'order-service',
      target: 'analytics-service',
      protocol: 'HTTP',
      endpoint: 'order.completed',
      confidence: 0.85,
      detectedBy: 'mq-analysis',
      sourceLocationCount: 2,
    },
    {
      id: 'e9',
      source: 'payment-service',
      target: 'notification-service',
      protocol: 'HTTP',
      endpoint: 'payment.completed',
      confidence: 0.91,
      detectedBy: 'mq-analysis',
      sourceLocationCount: 3,
    },
    {
      id: 'e10',
      source: 'user-service',
      target: 'auth-service',
      protocol: 'HTTP',
      method: 'GetUserCredentials',
      endpoint: 'AuthService/GetCredentials',
      confidence: 0.96,
      detectedBy: 'proto-analysis',
      sourceLocationCount: 5,
    },
    {
      id: 'e11',
      source: 'user-service',
      target: 'file-service',
      protocol: 'HTTP',
      method: 'POST',
      endpoint: '/files/upload',
      confidence: 0.87,
      detectedBy: 'static-analysis',
      sourceLocationCount: 2,
    },
    {
      id: 'e12',
      source: 'user-service',
      target: 'notification-service',
      protocol: 'HTTP',
      endpoint: 'user.registered',
      confidence: 0.9,
      detectedBy: 'mq-analysis',
      sourceLocationCount: 3,
    },
    {
      id: 'e13',
      source: 'frontend-bff',
      target: 'user-service',
      protocol: 'HTTP',
      method: 'GET',
      endpoint: '/users/profile',
      confidence: 0.94,
      detectedBy: 'static-analysis',
      sourceLocationCount: 4,
    },
    {
      id: 'e14',
      source: 'frontend-bff',
      target: 'search-service',
      protocol: 'HTTP',
      method: 'GET',
      endpoint: '/search',
      confidence: 0.92,
      detectedBy: 'static-analysis',
      sourceLocationCount: 3,
    },
    {
      id: 'e15',
      source: 'frontend-bff',
      target: 'file-service',
      protocol: 'HTTP',
      method: 'GET',
      endpoint: '/files/{id}',
      confidence: 0.88,
      detectedBy: 'static-analysis',
      sourceLocationCount: 2,
    },
    {
      id: 'e16',
      source: 'auth-service',
      target: 'config-service',
      protocol: 'HTTP',
      method: 'GET',
      endpoint: '/config/auth',
      confidence: 0.82,
      detectedBy: 'config-analysis',
      sourceLocationCount: 1,
    },
    {
      id: 'e17',
      source: 'inventory-service',
      target: 'analytics-service',
      protocol: 'HTTP',
      endpoint: 'inventory.updated',
      confidence: 0.86,
      detectedBy: 'mq-analysis',
      sourceLocationCount: 2,
    },
    {
      id: 'e18',
      source: 'search-service',
      target: 'analytics-service',
      protocol: 'HTTP',
      endpoint: 'search.query',
      confidence: 0.8,
      detectedBy: 'mq-analysis',
      sourceLocationCount: 1,
    },
    {
      id: 'e19',
      source: 'user-service',
      target: 'config-service',
      protocol: 'HTTP',
      method: 'GET',
      endpoint: '/config/user',
      confidence: 0.83,
      detectedBy: 'config-analysis',
      sourceLocationCount: 1,
    },
    {
      id: 'e20',
      source: 'order-service',
      target: 'config-service',
      protocol: 'HTTP',
      method: 'GET',
      endpoint: '/config/order',
      confidence: 0.81,
      detectedBy: 'config-analysis',
      sourceLocationCount: 1,
    },
  ],
  metadata: {
    projectId: 'demo-project',
    projectName: 'Demo MSA Project',
    analyzedAt: new Date().toISOString(),
    totalNodes: 12,
    totalEdges: 20,
    languages: ['nodejs', 'kotlin', 'java', 'typescript', 'python', 'go'],
  },
};

export const MOCK_PROJECTS: Project[] = [
  {
    id: 'demo-project',
    name: 'Demo MSA Project',
    repoUrl: 'https://github.com/example/demo-msa',
    language: 'polyglot',
    createdAt: new Date(Date.now() - 86400000 * 3).toISOString(),
    updatedAt: new Date().toISOString(),
    nodeCount: 12,
    edgeCount: 20,
  },
];

export const MOCK_SOURCE_DETAIL: SourceDetail = {
  dependency: {
    id: 'e5',
    source: 'order-service',
    target: 'payment-service',
    protocol: 'HTTP',
  },
  locations: [
    {
      id: 'loc1',
      filePath: 'src/main/kotlin/com/example/order/service/PaymentClient.kt',
      startLine: 24,
      endLine: 42,
      language: 'kotlin',
      githubUrl:
        'https://github.com/example/demo-msa/blob/main/order-service/src/main/kotlin/com/example/order/service/PaymentClient.kt#L24-L42',
      highlightLines: [28, 29, 30, 31, 32],
      content: `package com.example.order.service

import com.example.payment.grpc.PaymentServiceGrpc
import com.example.payment.grpc.ProcessPaymentRequest
import net.devh.boot.grpc.client.inject.GrpcClient
import org.springframework.stereotype.Service

@Service
class PaymentClient {

    @GrpcClient("payment-service")
    private lateinit var paymentStub: PaymentServiceGrpc.PaymentServiceBlockingStub

    fun processPayment(orderId: String, amount: Long, currency: String): PaymentResult {
        val request = ProcessPaymentRequest.newBuilder()
            .setOrderId(orderId)
            .setAmount(amount)
            .setCurrency(currency)
            .build()

        val response = paymentStub.processPayment(request)
        return PaymentResult(
            transactionId = response.transactionId,
            status = response.status.name
        )
    }
}`,
    },
    {
      id: 'loc2',
      filePath: 'src/main/kotlin/com/example/order/OrderService.kt',
      startLine: 55,
      endLine: 70,
      language: 'kotlin',
      githubUrl:
        'https://github.com/example/demo-msa/blob/main/order-service/src/main/kotlin/com/example/order/OrderService.kt#L55-L70',
      highlightLines: [60, 61, 62],
      content: `    fun createOrder(request: CreateOrderRequest): OrderResponse {
        // Validate inventory
        inventoryClient.reserveItems(request.items)

        val order = orderRepository.save(
            Order(
                userId = request.userId,
                items = request.items,
                totalAmount = request.totalAmount,
                status = OrderStatus.PENDING
            )
        )

        // Process payment via gRPC
        val paymentResult = paymentClient.processPayment(
            orderId = order.id,
            amount = order.totalAmount,
            currency = "KRW"
        )

        return OrderResponse.from(order, paymentResult)
    }`,
    },
  ],
  relatedConfig: [
    {
      filePath: 'src/main/resources/application.yml',
      key: 'grpc.client.payment-service.address',
      value: 'static://payment-service:9090',
      githubUrl:
        'https://github.com/example/demo-msa/blob/main/order-service/src/main/resources/application.yml',
    },
    {
      filePath: '.env',
      key: 'PAYMENT_SERVICE_HOST',
      value: 'payment-service',
      githubUrl: 'https://github.com/example/demo-msa/blob/main/order-service/.env',
    },
    {
      filePath: 'docker-compose.yml',
      key: 'services.order-service.environment.PAYMENT_SERVICE_HOST',
      value: 'payment-service',
      githubUrl: 'https://github.com/example/demo-msa/blob/main/docker-compose.yml',
    },
  ],
};
