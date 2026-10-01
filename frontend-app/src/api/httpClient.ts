import { ZodError, type ZodType } from 'zod'
import type { ApiProblem } from '../contracts/api'
import { apiProblemSchema } from '../contracts/schemas'

type HttpMethod = 'GET' | 'POST' | 'DELETE'

interface RequestOptions<T> {
  path: string
  schema: ZodType<T>
  method?: HttpMethod
  token?: string
  body?: unknown
  signal?: AbortSignal
}

interface VoidRequestOptions {
  path: string
  method: 'DELETE'
  token?: string
  signal?: AbortSignal
}

interface HttpClientOptions {
  baseUrl?: string
  fetcher?: typeof fetch
  sleep?: (milliseconds: number) => Promise<void>
  random?: () => number
}

export class ApiClientError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    public readonly detail: string,
    public readonly requestId: string | null,
    public readonly problem: ApiProblem | null,
  ) {
    super(detail)
    this.name = 'ApiClientError'
  }
}

export class ContractValidationError extends Error {
  constructor(
    public readonly path: string,
    public readonly requestId: string | null,
    public readonly issues: ZodError['issues'],
    options?: ErrorOptions,
  ) {
    super(`Backend response did not match the frozen contract for ${path}`, options)
    this.name = 'ContractValidationError'
  }
}

export class HttpClient {
  private readonly baseUrl: string
  private readonly fetcher: typeof fetch | undefined
  private readonly sleep: (milliseconds: number) => Promise<void>
  private readonly random: () => number

  constructor(options: HttpClientOptions = {}) {
    this.baseUrl = options.baseUrl ?? globalThis.location?.origin ?? 'http://localhost'
    this.fetcher = options.fetcher
    this.sleep = options.sleep ?? ((milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds)))
    this.random = options.random ?? Math.random
  }

  async request<T>(options: RequestOptions<T>): Promise<T> {
    const response = await this.execute(options)
    const requestId = response.headers.get('X-Request-ID')
    let payload: unknown
    try {
      payload = await response.json()
    } catch (cause) {
      throw new ContractValidationError(options.path, requestId, [], { cause })
    }
    const parsed = options.schema.safeParse(payload)
    if (!parsed.success) {
      throw new ContractValidationError(options.path, requestId, parsed.error.issues, { cause: parsed.error })
    }
    return parsed.data
  }

  async requestVoid(options: VoidRequestOptions): Promise<void> {
    const response = await this.execute(options)
    if (response.status !== 204) {
      throw new ContractValidationError(options.path, response.headers.get('X-Request-ID'), [])
    }
  }

  private async execute(options: Omit<RequestOptions<unknown>, 'schema'> | VoidRequestOptions): Promise<Response> {
    const method = options.method ?? 'GET'
    const maximumRetries = method === 'GET' ? 2 : 0
    let attempt = 0

    while (true) {
      try {
        const headers = new Headers({ Accept: 'application/json' })
        if (options.token !== undefined) headers.set('Authorization', `Bearer ${options.token}`)
        const hasBody = 'body' in options && options.body !== undefined
        if (hasBody) headers.set('Content-Type', 'application/json')
        const fetcher = this.fetcher ?? globalThis.fetch.bind(globalThis)
        const response = await fetcher(new URL(options.path, this.baseUrl), {
          method,
          headers,
          ...(hasBody ? { body: JSON.stringify(options.body) } : {}),
          ...(options.signal === undefined ? {} : { signal: options.signal }),
        })

        if (response.status >= 500 && attempt < maximumRetries) {
          await this.waitBeforeRetry(attempt++)
          continue
        }
        if (!response.ok) throw await this.toApiError(response)
        return response
      } catch (error) {
        if (error instanceof ApiClientError || error instanceof ContractValidationError) throw error
        if (options.signal?.aborted || (error instanceof DOMException && error.name === 'AbortError')) throw error
        if (attempt >= maximumRetries) throw error
        await this.waitBeforeRetry(attempt++)
      }
    }
  }

  private async toApiError(response: Response): Promise<ApiClientError> {
    const requestId = response.headers.get('X-Request-ID')
    let payload: unknown = null
    try { payload = await response.json() } catch { /* malformed error body */ }
    const parsed = apiProblemSchema.safeParse(payload)
    if (parsed.success) {
      return new ApiClientError(
        response.status, parsed.data.code, parsed.data.detail, requestId, parsed.data,
      )
    }
    return new ApiClientError(
      response.status,
      'UNEXPECTED_HTTP_ERROR',
      `Request failed with HTTP ${response.status}`,
      requestId,
      null,
    )
  }

  private waitBeforeRetry(attempt: number): Promise<void> {
    const baseDelay = 50 * (2 ** attempt)
    return this.sleep(baseDelay + Math.floor(this.random() * 25))
  }
}
