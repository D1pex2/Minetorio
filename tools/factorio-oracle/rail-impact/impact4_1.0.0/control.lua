local function emit(t) localised_print('MATRIX '..game.table_to_json(t)) end
local function ent(e)
 if not e or not e.valid then return nil end
 local t=(e.type=='locomotive' or e.type=='cargo-wagon' or e.type=='fluid-wagon') and e.train or nil
 return {name=e.name,unit=e.unit_number,health=e.health,position=e.position,speed=t and t.speed,weight=t and t.weight}
end
local function stacks(inv)
 local a={}
 if inv then for i=1,#inv do local st=inv[i]; if st.valid_for_read then a[#a+1]={name=st.name,count=st.count,type=st.type,health=st.health,entity_data=st.is_item_with_entity_data,label=st.is_item_with_entity_data and st.entity_label or nil} end end end
 return a
end
local function mine_case(c,tick)
 local e=c.target
 local before=ent(e)
 local fuel=e.type=='locomotive' and stacks(e.get_fuel_inventory()) or nil
 local burner=e.type=='locomotive' and {remaining=e.burner.remaining_burning_fuel,burning=e.burner.currently_burning and e.burner.currently_burning.name,heat=e.burner.heat} or nil
 local cargo=e.type=='cargo-wagon' and stacks(e.get_inventory(defines.inventory.cargo_wagon)) or nil
 local fluid=e.type=='fluid-wagon' and e.fluidbox[1] or nil
 local ok=c.character.mine_entity(e,false)
 emit({kind='mine',case=c.name,tick=tick,before=before,fuel=fuel,burner=burner,cargo=cargo,fluid=fluid,success=ok,target_valid=e.valid,items=stacks(c.character.get_main_inventory())})
end
script.on_init(function()
 local s=game.create_surface('matrix4',{seed=12345,water='none',starting_area='none',autoplace_controls={['enemy-base']={frequency='none',size='none',richness='none'}}})
 for x=0,128,32 do for y=-800,64,32 do s.request_to_generate_chunks({x,y},0) end end
 s.force_generate_chunk_requests()
 global.cases={}; global.mines={}
 for i=1,6 do
  local x=i*16
  for y=-750,40,2 do s.create_entity{name='straight-rail',position={x,y},direction=defines.direction.north,force='player'} end
  if i<=2 then
   local l=s.create_entity{name='locomotive',position={x,0},direction=defines.direction.north,force='player'}
   local a=s.create_entity{name='train-stop',position={x+2,-700},direction=defines.direction.north,force='player'}
   local target=s.create_entity{name=i==1 and 'cargo-wagon' or 'small-biter',position={x+1,-200},direction=defines.direction.north,force=i==1 and 'player' or 'enemy'}
   if not l or not a or not target then emit({kind='setup_error',case=i}); return end
   if i==1 then target.health=20 else target.active=false end
   local trailing=nil
   if i==2 then trailing=s.create_entity{name='cargo-wagon',position={x,7},direction=defines.direction.north,force='player'} end
   a.backer_name='Matrix'..i; l.get_fuel_inventory().insert{name='coal',count=30}
   global.cases[i]={loco=l,target=target,station=a,trailing=trailing}
   emit({kind='setup',case=i,loco=ent(l),target=ent(target),trailing=ent(trailing),target_resistances=target.prototype.resistances,target_mask=target.prototype.collision_mask,loco_mask=l.prototype.collision_mask})
  else
   local names={'locomotive','cargo-wagon','fluid-wagon','locomotive'}
   local name=names[i-2]
   local e=s.create_entity{name=name,position={x,0},direction=defines.direction.north,force='player'}
   local ch=s.create_entity{name='character',position={x+4,8},force='player'}
   if not e or not ch then emit({kind='mine_setup_error',case=i,entity=e~=nil,character=ch~=nil}); return end
   if i==3 or i==6 then e.get_fuel_inventory().insert{name='coal',count=7} end
   if i==4 then e.get_inventory(defines.inventory.cargo_wagon).insert{name='iron-plate',count=30}; e.health=275 end
   if i==5 then e.fluidbox[1]={name='water',amount=1234,temperature=42}; e.health=300 end
   local a=nil
   if i==6 then a=s.create_entity{name='train-stop',position={x+2,-700},direction=defines.direction.north,force='player'}; a.backer_name='MineWarm' end
   global.mines[#global.mines+1]={name=({'cold_loco','cargo_damaged','fluid_damaged','warm_loco'})[i-2],target=e,character=ch,station=a}
   emit({kind='mine_setup',case=i,target=ent(e),character=ent(ch)})
  end
 end
end)
script.on_event(defines.events.on_entity_damaged,function(e)
 emit({kind='damage',tick=e.tick,entity=ent(e.entity),cause=ent(e.cause),damage_type=e.damage_type.name,original=e.original_damage_amount,final=e.final_damage_amount,final_health=e.final_health})
end)
script.on_event(defines.events.on_entity_died,function(e) emit({kind='died',tick=e.tick,entity=ent(e.entity),cause=ent(e.cause),damage_type=e.damage_type and e.damage_type.name}) end)
script.on_event(defines.events.on_tick,function(e)
 for i,c in ipairs(global.cases or {}) do
  local l=c.loco
  if e.tick==0 and l.valid then l.train.schedule={current=1,records={{station='Matrix'..i,wait_conditions={{type='time',compare_type='and',ticks=60}}}}}; l.train.manual_mode=false end
  if l.valid then emit({kind='trace',case=i,tick=e.tick,loco=ent(l),target=ent(c.target),trailing=ent(c.trailing),state=l.train.state,path=l.train.has_path,fuel=l.burner.remaining_burning_fuel,heat=l.burner.heat,coal=l.get_fuel_inventory().get_item_count('coal')}) end
 end
 local m=global.mines or {}
 if e.tick==0 and m[4] and m[4].target.valid then local t=m[4].target.train; t.schedule={current=1,records={{station='MineWarm',wait_conditions={{type='time',compare_type='and',ticks=60}}}}}; t.manual_mode=false end
 if e.tick==1 then for i=1,3 do if m[i] then for attempt=1,5 do if m[i].target.valid then mine_case(m[i],e.tick) end end end end end
 if e.tick==60 and m[4] and m[4].target.valid then m[4].target.train.manual_mode=true end
 if e.tick>=61 and e.tick<=250 and m[4] and m[4].target.valid then
  local l=m[4].target; if e.tick==61 or e.tick==100 or l.train.speed==0 then emit({kind='warm_trace',tick=e.tick,loco=ent(l),remaining=l.burner.remaining_burning_fuel,burning=l.burner.currently_burning and l.burner.currently_burning.name,heat=l.burner.heat,coal=l.get_fuel_inventory().get_item_count('coal')}) end
  if l.train.speed==0 then mine_case(m[4],e.tick) end
 end
end)
