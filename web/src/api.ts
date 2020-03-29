import { API_URL } from 'environment';

export async function request<T>(path: string): Promise<ApiSuccess<T>>
{
    const json = await fetch(API_URL).then(r => r.json());
    if (!json.success) {
        throw json as ApiError;
    }
    
    return json as ApiSuccess<T>;
}

export class ApiResponse
{
    message: string;
    
    constructor(message: string)
    {
        this.message = message;
    }
}

export class ApiSuccess<T> extends ApiResponse
{
    data: T;
    
    constructor(message: string, data: T)
    {
        super(message);
        this.data = data;
    }
}

export class ApiError extends ApiResponse
{
}