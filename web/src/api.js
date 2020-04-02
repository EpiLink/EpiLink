const API_URL = 'http://localhost:9090/api/v1'; // TODO: Env

export default async function(method, path, body) {
    if (!body) {
        // Arguments are [path, body?]
        if (typeof path !== 'string') {
            body = path;
            path = method;
            method = 'GET';
        }
    }

    const params = { method };
    if (body) {
        params.body = JSON.stringify(body, null, 4);
        params.headers = {
            'Content-Type': 'application/json'
            // TODO: Token
        };
    }

    const result = await fetch(API_URL + path, params).then(r => r.json());
    if (!result.success) {
        console.error(`API returned an error during request '${method} {path}' : '${result.message}'`);
        throw result.message;
    }

    return result.data;
};