# RPS Service

* [Overview] (#overview)
* [Target] (#target)
* [Throttling Service Rules] (#throttlingServiceRules)
* [Acceptance criteria] (#acceptanceCriteria)
* [Implementation notes] (#implementationNotes)

## Overview

Реализовать сервис который позволяет ограничивать количество допустимых запросов в секунду 
(ЗВС или request per second RPS) для REST сервиса в соответствии с правилами для конкретных пользователей.

## Target

Сервис который возвращает максимально допустимый ЗВС для конкретного пользователя по его токену. 
У пользователя одновременно может быть несколько используемых токенов. 
В контексте данной задачи этот сервис реализовывать не нужно. 
Достаточно иметь моковую реализацию которая возвращает допустимый ЗВС для пары пользователей 
по паре токенов. Эту же реализацию можно использовать и в тесте.

## Throttling Service Rules

- Если токен не предоставлен тогда пользователь считается неавторизованным.
- Все неавторизованные пользователи лимитированы GuestRPS, 
  (передается параметром при создании ThrottlingServiceImpl, может быть равен для примера 20 
  или любое другое целое число).
- Если токен пришел в запросе но SlaService не вернул еще SLA по пользователю то использовать GuestRPS.
- ЗВС должен расчитываться по пользователю т.к. один и тот же пользователь может иметь разные токены.
- SLA меняется довольно редко, а SlaService довольно долгий при вызове (~200 - 300 миллисекунд на запрос). Поэтому предусмотрите кеширование SLA, и не выполняйте запрос к SlaService если запрос по такому токену уже в процессе.
- Учтите что среднее время отклика REST сервиса должно быть не более 5 миллисекунд. 
  т.е. вызовы ThrottlingService не должны влиять на допустимую скорость работы REST сервиса.

## Acceptance Criteria

- Реализовать class ThrottlingServiceImpl implements ThrottlingService
- Покрыть реализацию тестами которые доказывают правильность его работы.


Дополнительно (по желанию и наличию времени):
- реализовать любой REST сервис использующий данную реализацию ThrottlingService.
- написать тест на нагрузочное тестирование данного REST сервиса
- сравнить скорость работы REST сервиса с использованием ThrottlingService и без.


Для нас важна именно ваша реализация этого задания. т.е. не разрешается копирование 
решения из интернета или реализация кем либо еще кроме вас. 
Можно использовать любые вспомогательные библиотеки, кроме собственно являющихся 
реализацией подобного сервиса. Лучше написать решение с минимальным функционалом но работающее, 
чем включающее все требования но не работающее.

## Implementation notes

* Using Lombok? Not necessary
* Do not distinguish modules with Interfaces and implementation. Ideally if we are going to
  have different implementations/mocks/stubs and etc it would be better to move interfaces in separate 
  module. ( if we follow multi-module /microservices oriented architecture)
* (mock, service, dto) packages in one module to simplify development of MVP
* ThrottlingService.isRequestAllowed(String token) - intentional decision. Do not use Optional in
  method signature
* one module, no parent pom. That's why explicitly defined versions in dependency. (No dependencyManagement)