<!--

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.

    This Source Code Form is "Incompatible With Secondary Licenses", as
    defined by the Mozilla Public License, v. 2.0.

-->
<template>
    <div id="success">
        <link-check />

        <p id="message">
            {{ $t('success.created') }}<br />
            {{ $t('success.access') }}

            <span id="close" v-html="$t('success.close')" />
        </p>

        <router-link id="profile" :to="{ name: 'profile' }" v-html="$t('success.profile')" />
    </div>
</template>

<script>
    import LinkCheck from '../components/Check.vue';

    export default {
        name: 'link-success',
        components: { LinkCheck }
    };
</script>

<style lang="scss" scoped>
    @import '../styles/vars';

    #success {
        display: flex;
        justify-content: space-evenly;
        align-items: center;
        flex-direction: column;

        padding: 20px 45px 0;

        overflow-y: auto;
    }

    #message {
        text-align: center;
        font-size: 19px;

        #close {
            display: block;
            margin-top: 10px;

            font-weight: bold;
        }
    }

    #profile {
        text-decoration: underline;
        padding-bottom: 20px;
    }

    @media screen and (max-width: 415px) {
        #success {
            padding: 20px 30px 0;
        }
    }

    @media screen and (max-width: $height-wrap-breakpoint) {
        #success {
            justify-content: normal;
        }
    }
</style>
