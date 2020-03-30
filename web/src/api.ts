import { API_URL } from 'environment';

export async function request<T>(path: string): Promise<T>
{
    const json = await fetch(API_URL, { method: 'POST' }).then(r => r.json());
    if (!json.success) {
        throw json as ApiError;
    }
    
    return (json as ApiSuccess<T>).data;
}

export interface ApiResponse
{
    message: string;
}

export interface ApiSuccess<T> extends ApiResponse
{
    data: T;
}

export interface ApiError extends ApiResponse
{
}