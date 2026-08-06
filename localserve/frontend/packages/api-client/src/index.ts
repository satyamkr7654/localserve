import type { ApiProblem } from "@localserve/contracts";
import type { ZodType } from "zod";

export class LocalServeApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    safeMessage: string,
    public readonly correlationId?: string,
  ) {
    super(safeMessage);
    this.name = "LocalServeApiError";
  }
}

export type RequestOptions<T> = {
  schema: ZodType<T>;
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  idempotencyKey?: string;
  expectedVersion?: number;
  signal?: AbortSignal;
  cache?: RequestCache;
};

export type ApiClientOptions = {
  baseUrl: string;
  timeoutMs?: number;
  csrfToken?: () => string | undefined;
  fetchImplementation?: typeof fetch;
};

export class ApiClient {
  private readonly fetcher: typeof fetch;
  private readonly timeoutMs: number;

  constructor(private readonly options: ApiClientOptions) {
    this.fetcher = options.fetchImplementation ?? fetch;
    this.timeoutMs = options.timeoutMs ?? 10_000;
  }

  async request<T>(path: `/${string}`, options: RequestOptions<T>): Promise<T> {
    const method = options.method ?? "GET";
    const response = await this.execute(path, method, options, method === "GET" ? 1 : 0);
    if (response.status === 204) return options.schema.parse(undefined);
    const payload: unknown = await response.json().catch(() => undefined);
    if (!response.ok) throw problem(response.status, payload, response.headers.get("X-Correlation-ID") ?? undefined);
    return options.schema.parse(payload);
  }

  private async execute<T>(path: `/${string}`, method: NonNullable<RequestOptions<T>["method"]>,
                           options: RequestOptions<T>, retries: number): Promise<Response> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(new DOMException("Request timed out", "TimeoutError")), this.timeoutMs);
    const abort = () => controller.abort(options.signal?.reason);
    options.signal?.addEventListener("abort", abort, { once: true });
    try {
      const headers = new Headers({ Accept: "application/json" });
      if (options.body !== undefined) headers.set("Content-Type", "application/json");
      if (options.idempotencyKey) headers.set("Idempotency-Key", options.idempotencyKey);
      if (options.expectedVersion !== undefined) headers.set("If-Match", `"v${options.expectedVersion}"`);
      const csrf = this.options.csrfToken?.();
      if (csrf) headers.set("X-CSRF-Token", csrf);
      const init: RequestInit = {
        method, headers, credentials: "include", cache: options.cache ?? "no-store", signal: controller.signal,
      };
      if (options.body !== undefined) init.body = JSON.stringify(options.body);
      const response = await this.fetcher(`${this.options.baseUrl}${path}`, init);
      if (retries > 0 && [502, 503, 504].includes(response.status)) {
        await delay(150 + Math.floor(Math.random() * 100), controller.signal);
        return this.execute(path, method, options, retries - 1);
      }
      return response;
    } finally {
      clearTimeout(timeout);
      options.signal?.removeEventListener("abort", abort);
    }
  }
}

export function newIdempotencyKey(): string {
  return `web-${crypto.randomUUID()}`;
}

function problem(status: number, payload: unknown, fallbackCorrelationId?: string): LocalServeApiError {
  if (isProblem(payload)) {
    return new LocalServeApiError(status, payload.code, payload.detail, payload.correlationId ?? fallbackCorrelationId);
  }
  return new LocalServeApiError(status, "SYSTEM.UNEXPECTED_RESPONSE", "The service returned an unexpected response", fallbackCorrelationId);
}

function isProblem(value: unknown): value is ApiProblem {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return typeof candidate.code === "string" && typeof candidate.detail === "string";
}

function delay(milliseconds: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(resolve, milliseconds);
    signal.addEventListener("abort", () => { clearTimeout(timer); reject(signal.reason); }, { once: true });
  });
}
