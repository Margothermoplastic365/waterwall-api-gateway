# API Spec Samples

Sample files for testing the API Import feature. REST samples point to **real public APIs** with live backends.

## REST Samples (Real Live APIs — verified working)

| # | File | Backend | Test Command |
|---|------|---------|-------------|
| 01 | `01-openapi3-petstore.json` | petstore3.swagger.io | `curl https://petstore3.swagger.io/api/v3/pet/findByStatus?status=available` |
| 02 | `02-openapi3-jsonplaceholder.json` | jsonplaceholder.typicode.com | `curl https://jsonplaceholder.typicode.com/posts/1` |
| 03 | `03-openapi3-github.json` | api.github.com | `curl https://api.github.com/users/octocat` |
| 04 | `04-openapi3-httpbin.json` | httpbin.org | `curl https://httpbin.org/get` |
| 05 | `05-openapi3-catfact.json` | catfact.ninja | `curl https://catfact.ninja/fact` |

## Protocol-Specific Samples

| # | File | Format | Protocol |
|---|------|--------|----------|
| 03-async | `03-asyncapi-orders.json` | AsyncAPI 2.6 | Kafka |
| 04-gql | `04-graphql-blog.graphql` | GraphQL SDL | GraphQL |
| 05-grpc | `05-grpc-user-service.proto` | Protobuf | gRPC |
| 06 | `06-wsdl-payment.wsdl` | WSDL | SOAP |
| 07 | `07-postman-weather.json` | Postman Collection | REST |
| 08 | `08-openrpc-calculator.json` | OpenRPC | JSON-RPC |
| 09 | `09-odata-northwind.xml` | OData EDMX | OData |
| 10 | `10-har-sample-traffic.har` | HAR | REST (discovered) |

## How to Test

1. Go to Admin Console → APIs → Import API (`http://localhost:3001/apis/import`)
2. Upload a file or paste content
3. Click "Preview Import" → verify parsed results
4. Click "Import as DRAFT" → API created

## Expected Parse Results

| File | Name | Routes | Auth |
|------|------|--------|------|
| 01 petstore | Swagger Petstore | 8 routes | API_KEY |
| 02 jsonplaceholder | JSONPlaceholder API | 10 routes | — |
| 03 github | GitHub Public API | 6 routes | — |
| 04 httpbin | httpbin API | 12 routes | BASIC, OAUTH2 |
| 05 catfact | Cat Facts API | 3 routes | — |
| 03-async orders | Order Events API | 4 channels | — |
| 04-gql blog | GraphQL API | 1 route (POST /graphql) | — |
| 05-grpc user | UserService Service | 8 routes | — |
| 06 wsdl payment | PaymentService | 3 routes | — |
| 07 postman weather | Weather Service Collection | 7 routes | API_KEY |
| 08 openrpc calc | Calculator JSON-RPC Service | 1 route (POST /rpc) | — |
| 09 odata northwind | OData Service | 24 routes | — |
| 10 har traffic | Discovered API | 9 routes | — |
