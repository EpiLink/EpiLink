<!--

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.

    This Source Code Form is "Incompatible With Secondary Licenses", as
    defined by the Mozilla Public License, v. 2.0.

-->
<template>
    <div class="user">
        <img class="avatar" alt="Avatar" :src="user.avatar || 'https://raw.githubusercontent.com/EpiLink/EpiLink/master/assets/unknownuser256.png'" />
        <div class="username">
            <span class="admin-badge" v-if="user.privileged">ADMIN</span>
            <span>
                <span class="username-text">{{ nick }}</span>
                <span class="tag">{{ tag }}</span>
            </span>
        </div>
        <span class="email" v-if="user.email">{{ user.email }}</span>
    </div>
</template>

<script>
    import { mapState } from 'vuex';

    export default {
        name: 'link-user',

        computed: {
            ...mapState({ user: state => state.auth.user }),
            nick() {
                const { username } = this.user;
                return username.substring(0, username.indexOf('#'));
            },
            tag() {
                const { username } = this.user;
                return username.substring(username.indexOf('#'));
            }
        }
    };
</script>

<style lang="scss" scoped>
    @import '../styles/vars';
    @import '../styles/mixins';

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

        text-align: center;

        .username-text {
            overflow-wrap: break-word;
            word-break: break-word;
        }

        .admin-badge {
            background-color: #2DA62D;
            border-radius: 3px;
            padding: 8px;
            font-size: .6em;
            margin-right: 12px;
            align-self: center;
            color: white;
        }

        .tag {
            font-weight: normal;
        }
    }

    .email {
        font-size: 18px;
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
