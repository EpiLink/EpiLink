const API_URL = 'http://localhost:9090/api/v1'; // TODO: Env

// Handle down backend ?

const UNLOGGED_TOKEN_HEADER = 'RegistrationSessionId';
const LOGGED_TOKEN_HEADER = 'SessionId';

const stored = localStorage.getItem('session');
let session = stored ? JSON.parse(stored) : null;

if (session) {
    console.log(`Got saved session id type '${session.type}' and token '${session.token}'`);
}

export function getRedirectURI(service) {
    return window.location.origin + '/redirect/' + service;
}

export function deleteSession() {
    session = null;
    localStorage.setItem('session',  null);
}

/**
 * Performs an asynchrone HTTP request to the backend API.
 * The only required arguments is the 'path', any optional argument can be given
 *
 * Example : request('/auth/apply', { session obj }) is a valid call
 *
 * @param method (optional) The request method (GET, POST, or DELETE)
 * @param path The request path, without /api/v1, starting with a slash (example '/meta/info')
 * @param body (optional) The request body object that will be encoded in JSON
 *
 * @returns {Promise<Object>} A Promise that resolves with the request result data, or fails with the request error message
 */
export default async function(method, path, body) {
    if (!body && typeof path !== 'string') {
        // [path, body?]
        body = path;
        path = method;
        method = 'GET';
    }
    // else [method, path, body?]

    const params = {
        method,
        headers: {}
    };

    if (body) {
        params.body = JSON.stringify(body, null, 4);
        params.headers['Content-Type'] = 'application/json';
    }

    if (session) {
        params.headers[session.type === 'unlogged' ? UNLOGGED_TOKEN_HEADER : LOGGED_TOKEN_HEADER] = session.token;
    }

    const result = await fetch(API_URL + path, params);
    const json = await result.json();

    if (!json.success) {
        console.error(`API returned an error during request '${method} ${path}' : '${json.message}'`);
        throw json.message;
    }

    const checkSession = (type, header) => {
        if (result.headers.get(header)) {
            const token = result.headers.get(header);
            if (session && type === session.type && token === session.token) {
                return;
            }

            localStorage.setItem('session', JSON.stringify({ type, token }));
            console.log(`Session retrieved, token is '${token}'`);
        }
    };

    checkSession('unlogged', UNLOGGED_TOKEN_HEADER);
    checkSession('logged', LOGGED_TOKEN_HEADER);

    return json.data;
};