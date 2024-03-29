/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
import { isMobile } from './util';

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

export function openPopup(title, service, stub) {
    const url = `${stub}&redirect_uri=${getRedirectURI(service)}`;
    if (isMobile()) {
        window.location.href = url;
        return;
    }

    const width = Math.min(900, screen.width - 50), height = 800;
    const x = screen.width / 2 - width / 2, y = screen.height / 2 - height / 2 - 65;
    const options = `menubar=no, status=no, scrollbars=no, menubar=no, width=${width}, height=${height}, top=${y}, left=${x}`;

    return window.open(url, `EpiLink - ${title}`, options);
}

export function deleteSession() {
    session = null;
    localStorage.setItem('session', null);
}

export function isPermanentSession() {
    return session && session.type === 'logged';
}

/**
 * Performs an asynchronous HTTP request to the backend API.
 * The only required arguments is the 'path', any optional argument can be given
 *
 * Example : request('/auth/apply', { session obj }) is a valid call
 *
 * @param method (optional) The request method (GET, POST, or DELETE)
 * @param path The request path, without /api/v1, starting with a slash (example '/meta/info')
 * @param body (optional) The request body object that will be encoded in JSON
 * @param returnRawResponse (optional) True if the raw response should be returned instead of the returned JSON object.
 *
 * @returns {Promise<Object>} A Promise that resolves with the request result data, or fails with the request error message
 */
export default async function (method, path, body, returnRawResponse = false) {
    if (!body && typeof path !== 'string') {
        // [path, body?]
        body = path;
        path = method;
        method = 'GET';
    }
    // else [method, path, body?]

    const params = {
        method,
        headers: {
            'Accept': 'application/json'
        }
    };

    if (body) {
        params.body = JSON.stringify(body, null, 4);
        params.headers['Content-Type'] = 'application/json';
    }

    if (session) {
        params.headers[session.type === 'unlogged' ? UNLOGGED_TOKEN_HEADER : LOGGED_TOKEN_HEADER] = session.token;
    }

    const result = await fetch(BACKEND_URL + path, params);
    if (returnRawResponse) {
        return result;
    }
    const text = await result.text();
    if (!text) {
        console.warn(`API returned an empty response during request '${method} ${path}'`);
        return null;
    }

    let json;
    try {
        json = JSON.parse(text);
    } catch (err) {
        console.warn(`API didn't return JSON text during request '${method} ${path}', returning raw text`);
        return text;
    }

    if (!json.success) {
        console.error(`API returned an error during request '${method} ${path}' : '${json.message}'`);
        if (result.status === 429) {
            // Handle the 429 status separately, as the message is not super clear (and there's no guarantee of there
            // being a message in the first place)
            throw 'rate-limit';
        } else {
            // Do not throw the message directly, throw the I18n key instead with the replacements
            throw { key: 'backend.' + json.message_i18n, replace: json.message_i18n_data };
        }
    }

    const checkSession = (type, header) => {
        if (result.headers.get(header)) {
            const token = result.headers.get(header);
            if (session && type === session.type && token === session.token) {
                return;
            }

            session = { type, token };
            localStorage.setItem('session', JSON.stringify(session));

            console.log(`Session of type '${type}' retrieved, token is '${token}'`);
        }
    };

    checkSession('unlogged', UNLOGGED_TOKEN_HEADER);
    checkSession('logged', LOGGED_TOKEN_HEADER);

    return json.data;
};
