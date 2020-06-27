<!--

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.

    This Source Code Form is "Incompatible With Secondary Licenses", as
    defined by the Mozilla Public License, v. 2.0.

-->
<template>
    <div class="user">
        <img class="avatar" :src="user.avatar" />
        <div class="username">
            <span>{{ user.username | nick }}</span>
            <span class="tag">{{ user.username | tag }}</span>
        </div>
        <span class="email" v-if="user.email">{{ user.email }}</span>
    </div>
</template>

<script>
    import { mapState } from 'vuex';

    export default {
        name: 'link-user',

        computed: mapState({ user: state => state.auth.user }),
        filters: {
            nick(s) {
                return s.substring(0, s.indexOf('#'));
            },
            tag(s) {
                return s.substring(s.indexOf('#'));
            }
        }
    }
</script>

<style lang="scss" scoped>
    @import '../styles/vars';
    @import 'src/styles/mixins';

    .user {
        display: flex;
        flex-direction: column;
        align-items: center;
    }

    .avatar {
        border-radius: 50%;

        width: 128px;
        height: 128px;
    }

    .username {
        display: flex;
        margin-top: 15px;

        @include lato(bold);
        font-size: 30px;

        .tag {
            font-style: italic;
            font-weight: normal;
        }
    }

    .email {
        font-size: 18px;
        font-style: italic;
    }

    @media screen and (max-width: $height-wrap-breakpoint) {
        .avatar {
            width: 116px;
            height: 116px;
        }

        .username {
            font-size: 26px;
            margin-top: 10px;
        }
    }
</style>